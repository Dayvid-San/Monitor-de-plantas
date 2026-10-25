#include <math.h>
#include <stdint.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_task_wdt.h"

#include "plant_config.h"
#include "wifi_connect.h"
#include "soil_sensor.h"
#include "dht22.h"
#include "pump.h"
#include "backend_client.h"

static const char *TAG = "plant_node";

// Trava contra reservatório vazio / mangueira solta: se a bomba já rodou
// PUMP_MAX_FAILED_CYCLES vezes seguidas sem a umidade voltar a ficar acima do
// mínimo, a rega AUTOMÁTICA daquele canal para (evita bomba rodando a seco a
// cada cooldown). Reabilita sozinha quando a umidade volta ao normal, ao receber
// "Regar agora" no dashboard ou ao reiniciar. Pode ser sobrescrito em plant_config.h.
#ifndef PUMP_MAX_FAILED_CYCLES
#define PUMP_MAX_FAILED_CYCLES 3
#endif

// Estado por canal, atualizado em runtime (a partir do dashboard, quando
// disponível; caindo para os defaults de plant_config.h quando offline).
static remote_plant_config_t s_effective[PLANT_CHANNEL_COUNT];
static int64_t s_last_water_us[PLANT_CHANNEL_COUNT];
static bool s_watered_since_last_report[PLANT_CHANNEL_COUNT];
static int s_failed_cycles[PLANT_CHANNEL_COUNT];
static bool s_lockout_logged[PLANT_CHANNEL_COUNT];

static void init_effective_config(void)
{
    for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
        s_effective[i].channel = PLANT_CHANNELS[i].channel;
        s_effective[i].moisture_min = PLANT_CHANNELS[i].moisture_min_pct;
        s_effective[i].moisture_max = PLANT_CHANNELS[i].moisture_max_pct;
        s_effective[i].auto_water = true;
        s_last_water_us[i] = 0;
        s_watered_since_last_report[i] = false;
        s_failed_cycles[i] = 0;
        s_lockout_logged[i] = false;
    }
}

static void refresh_effective_config(void)
{
    remote_plant_config_t remote[PLANT_CHANNEL_COUNT];
    size_t count = 0;
    if (backend_fetch_config(CONFIG_PLANT_DEVICE_ID, remote, PLANT_CHANNEL_COUNT, &count) != ESP_OK) {
        return; // mantém os valores atuais (locais ou do último fetch bem-sucedido)
    }
    for (size_t r = 0; r < count; r++) {
        for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
            if (s_effective[i].channel == remote[r].channel) {
                s_effective[i] = remote[r];
                break;
            }
        }
    }
}

static int find_channel_index(int channel)
{
    for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
        if (PLANT_CHANNELS[i].channel == channel) return (int)i;
    }
    return -1;
}

// Liga a bomba do canal até atingir moisture_max, até pump_max_seconds (o
// que vier primeiro) — trava de segurança contra encharcar o vaso caso a
// leitura de umidade fique presa ou o sensor seja removido da terra.
static void run_pump_cycle(size_t idx)
{
    const plant_channel_t *cfg = &PLANT_CHANNELS[idx];
    ESP_LOGI(TAG, "Regando canal %d por até %ds", cfg->channel, cfg->pump_max_seconds);

    pump_set(cfg->pump_gpio, true);
    for (int elapsed = 0; elapsed < cfg->pump_max_seconds; elapsed++) {
        vTaskDelay(pdMS_TO_TICKS(1000));
        esp_task_wdt_reset();
        int pct = soil_sensor_read_pct(cfg->soil_adc_channel);
        if (pct >= 0 && pct >= s_effective[idx].moisture_max) break;
    }
    pump_set(cfg->pump_gpio, false);

    s_last_water_us[idx] = esp_timer_get_time();
    s_watered_since_last_report[idx] = true;
}

static void handle_pending_commands(void)
{
    pending_command_t commands[PLANT_CHANNEL_COUNT];
    size_t count = 0;
    if (backend_fetch_commands(CONFIG_PLANT_DEVICE_ID, commands, PLANT_CHANNEL_COUNT, &count) != ESP_OK) {
        return;
    }
    for (size_t i = 0; i < count; i++) {
        if (!commands[i].water_now) continue;
        int idx = find_channel_index(commands[i].channel);
        if (idx >= 0) {
            // Rega manual = o usuário está de olho (ex.: reabasteceu o reservatório): reabilita a rega automática.
            s_failed_cycles[idx] = 0;
            s_lockout_logged[idx] = false;
            run_pump_cycle((size_t)idx);
        }
    }
}

static void read_and_report(void)
{
    dht22_reading_t dht = { .temperature_c = NAN, .humidity_pct = NAN };
    if (dht22_read(DHT22_GPIO, &dht) != ESP_OK) {
        ESP_LOGW(TAG, "Falha na leitura do DHT22");
        dht.temperature_c = NAN;
        dht.humidity_pct = NAN;
    }
    int light_pct = light_sensor_read_pct();

    plant_reading_t readings[PLANT_CHANNEL_COUNT];
    for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
        const plant_channel_t *cfg = &PLANT_CHANNELS[i];
        int soil_pct = soil_sensor_read_pct(cfg->soil_adc_channel);

        bool cooldown_elapsed =
            (esp_timer_get_time() - s_last_water_us[i]) >= ((int64_t)PUMP_COOLDOWN_SEC * 1000000LL);

        if (soil_pct >= s_effective[i].moisture_min) {
            // Voltou ao normal: a rega funcionou (ou alguém regou), zera a contagem de falhas.
            s_failed_cycles[i] = 0;
            s_lockout_logged[i] = false;
        }

        if (s_effective[i].auto_water && soil_pct >= 0 && soil_pct < s_effective[i].moisture_min &&
            cooldown_elapsed) {
            if (s_failed_cycles[i] >= PUMP_MAX_FAILED_CYCLES) {
                if (!s_lockout_logged[i]) {
                    ESP_LOGE(TAG, "Canal %d: %d regas seguidas sem a umidade subir — rega automática suspensa. "
                                  "Verifique reservatório/mangueira e use \"Regar agora\" para reabilitar.",
                             cfg->channel, PUMP_MAX_FAILED_CYCLES);
                    s_lockout_logged[i] = true;
                }
            } else {
                s_failed_cycles[i]++;
                run_pump_cycle(i);
                soil_pct = soil_sensor_read_pct(cfg->soil_adc_channel);
            }
        }

        readings[i].channel = cfg->channel;
        readings[i].moisture_pct = soil_pct >= 0 ? (float)soil_pct : NAN;
        readings[i].pump_active = s_watered_since_last_report[i];
        s_watered_since_last_report[i] = false;
    }

    esp_err_t err = backend_send_readings(
        CONFIG_PLANT_DEVICE_ID, dht.temperature_c, dht.humidity_pct,
        light_pct >= 0 ? (float)light_pct : NAN, readings, PLANT_CHANNEL_COUNT);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Não foi possível enviar leituras ao backend agora");
    }
}

void app_main(void)
{
    init_effective_config();

    // Sem Wi-Fi no boot não aborta: a rega automática roda offline e as leituras
    // passam a ser enviadas assim que a reconexão em segundo plano funcionar.
    if (wifi_connect_start(CONFIG_PLANT_WIFI_SSID, CONFIG_PLANT_WIFI_PASSWORD) != ESP_OK) {
        ESP_LOGW(TAG, "Iniciando sem Wi-Fi; regra de rega local continua ativa");
    }
    ESP_ERROR_CHECK(soil_sensor_init());
    ESP_ERROR_CHECK(pump_init_all());

    ESP_LOGI(TAG, "Plant node \"%s\" pronto, %d canal(is) configurado(s)",
             CONFIG_PLANT_DEVICE_ID, (int)PLANT_CHANNEL_COUNT);

    // Watchdog: se este laço travar (por exemplo preso numa chamada de rede), o chip
    // reinicia em vez de deixar um relé ligado indefinidamente. Timeout em sdkconfig.defaults (30 s).
    if (esp_task_wdt_add(NULL) != ESP_OK) {
        ESP_LOGW(TAG, "Watchdog de tarefa indisponível (apague o sdkconfig e rode 'idf.py reconfigure' para aplicar o sdkconfig.defaults)");
    }

    int64_t next_read_s = 0;
    int64_t next_poll_s = 0;

    while (1) {
        esp_task_wdt_reset();
        int64_t now_s = esp_timer_get_time() / 1000000;

        if (now_s >= next_poll_s) {
            handle_pending_commands();
            next_poll_s = now_s + CONFIG_PLANT_COMMAND_POLL_INTERVAL_SEC;
        }

        esp_task_wdt_reset();
        if (now_s >= next_read_s) {
            refresh_effective_config();
            esp_task_wdt_reset();
            read_and_report();
            next_read_s = now_s + CONFIG_PLANT_READ_INTERVAL_SEC;
        }

        vTaskDelay(pdMS_TO_TICKS(1000));
    }
}
