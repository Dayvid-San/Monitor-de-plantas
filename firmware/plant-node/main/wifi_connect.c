#include "wifi_connect.h"

#include <string.h>
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"

static const char *TAG = "wifi_connect";
static EventGroupHandle_t s_wifi_event_group;
#define WIFI_CONNECTED_BIT BIT0
// Tempo máximo que wifi_connect_start() bloqueia no boot. Passado isso ele
// retorna ESP_ERR_TIMEOUT, mas as tentativas de reconexão continuam em segundo plano.
#define WIFI_BOOT_TIMEOUT_MS 30000
// Loga só a 1ª tentativa e depois a cada N, para não inundar o serial com o roteador fora do ar.
#define WIFI_LOG_EVERY_N_RETRIES 10

static int s_retry_num = 0;

static void event_handler(void *arg, esp_event_base_t event_base, int32_t event_id, void *event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START) {
        esp_wifi_connect();
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        // Reconecta sempre, sem limite de tentativas: um roteador fora do ar por
        // alguns minutos não pode deixar o ESP32 offline até alguém reiniciá-lo.
        xEventGroupClearBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
        esp_wifi_connect();
        s_retry_num++;
        if (s_retry_num == 1 || s_retry_num % WIFI_LOG_EVERY_N_RETRIES == 0) {
            ESP_LOGW(TAG, "Wi-Fi desconectado, tentativa de reconexão %d", s_retry_num);
        }
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        ESP_LOGI(TAG, "IP obtido: " IPSTR, IP2STR(&event->ip_info.ip));
        s_retry_num = 0;
        xEventGroupSetBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
    }
}

esp_err_t wifi_connect_start(const char *ssid, const char *password)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    s_wifi_event_group = xEventGroupCreate();

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, &event_handler, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, &event_handler, NULL));

    wifi_config_t wifi_config = { 0 };
    strncpy((char *)wifi_config.sta.ssid, ssid, sizeof(wifi_config.sta.ssid) - 1);
    strncpy((char *)wifi_config.sta.password, password, sizeof(wifi_config.sta.password) - 1);
    wifi_config.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    ESP_LOGI(TAG, "Conectando ao SSID \"%s\"...", ssid);
    EventBits_t bits = xEventGroupWaitBits(
        s_wifi_event_group, WIFI_CONNECTED_BIT, pdFALSE, pdFALSE, pdMS_TO_TICKS(WIFI_BOOT_TIMEOUT_MS));

    if (bits & WIFI_CONNECTED_BIT) {
        ESP_LOGI(TAG, "Conectado ao Wi-Fi");
        return ESP_OK;
    }
    ESP_LOGW(TAG, "Sem Wi-Fi após %d ms; seguindo offline e tentando reconectar em segundo plano",
             WIFI_BOOT_TIMEOUT_MS);
    return ESP_ERR_TIMEOUT;
}
