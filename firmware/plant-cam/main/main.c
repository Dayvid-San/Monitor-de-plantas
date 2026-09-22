#include <stdint.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_camera.h"

#include "camera_pins.h"
#include "wifi_connect.h"
#include "upload_client.h"

static const char *TAG = "plant_cam";

static esp_err_t camera_init(void)
{
    camera_config_t config = {
        .pin_pwdn = CAM_PIN_PWDN,
        .pin_reset = CAM_PIN_RESET,
        .pin_xclk = CAM_PIN_XCLK,
        .pin_sccb_sda = CAM_PIN_SIOD,
        .pin_sccb_scl = CAM_PIN_SIOC,
        .pin_d7 = CAM_PIN_D7,
        .pin_d6 = CAM_PIN_D6,
        .pin_d5 = CAM_PIN_D5,
        .pin_d4 = CAM_PIN_D4,
        .pin_d3 = CAM_PIN_D3,
        .pin_d2 = CAM_PIN_D2,
        .pin_d1 = CAM_PIN_D1,
        .pin_d0 = CAM_PIN_D0,
        .pin_vsync = CAM_PIN_VSYNC,
        .pin_href = CAM_PIN_HREF,
        .pin_pclk = CAM_PIN_PCLK,
        .xclk_freq_hz = 20000000,
        .ledc_timer = LEDC_TIMER_0,
        .ledc_channel = LEDC_CHANNEL_0,
        .pixel_format = PIXFORMAT_JPEG,
        .frame_size = FRAMESIZE_SVGA, // 800x600, bom equilíbrio qualidade/tamanho
        .jpeg_quality = 12,           // 0-63, menor = melhor qualidade
        .fb_count = 1,
        .fb_location = CAMERA_FB_IN_PSRAM,
        .grab_mode = CAMERA_GRAB_LATEST,
    };

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao iniciar câmera: %s", esp_err_to_name(err));
    }
    return err;
}

void app_main(void)
{
    ESP_ERROR_CHECK(wifi_connect_start(CONFIG_PLANT_CAM_WIFI_SSID, CONFIG_PLANT_CAM_WIFI_PASSWORD));
    ESP_ERROR_CHECK(camera_init());

    ESP_LOGI(TAG, "Plant cam \"%s\" pronta, capturando a cada %ds",
             CONFIG_PLANT_CAM_ID, CONFIG_PLANT_CAM_CAPTURE_INTERVAL_SEC);

    while (1) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            ESP_LOGW(TAG, "Falha ao capturar frame");
        } else {
            esp_err_t err = upload_photo(
                CONFIG_PLANT_CAM_BACKEND_HOST, CONFIG_PLANT_CAM_BACKEND_PORT,
                CONFIG_PLANT_CAM_ID, CONFIG_PLANT_CAM_PLANT_ID, fb->buf, fb->len);
            if (err != ESP_OK) {
                ESP_LOGW(TAG, "Falha ao enviar foto ao backend");
            }
            esp_camera_fb_return(fb);
        }

        vTaskDelay(pdMS_TO_TICKS((uint32_t)CONFIG_PLANT_CAM_CAPTURE_INTERVAL_SEC * 1000));
    }
}
