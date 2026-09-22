#include "soil_sensor.h"
#include "plant_config.h"

#include <stdbool.h>
#include "esp_log.h"
#include "soc/soc_caps.h"

static const char *TAG = "soil_sensor";
static adc_oneshot_unit_handle_t s_adc1_handle = NULL;
static bool s_channel_configured[SOC_ADC_CHANNEL_NUM] = { 0 };

static esp_err_t ensure_channel_configured(adc_channel_t channel)
{
    if (s_channel_configured[channel]) return ESP_OK;

    adc_oneshot_chan_cfg_t chan_cfg = {
        .bitwidth = ADC_BITWIDTH_DEFAULT,
        .atten = ADC_ATTEN_DB_12, // faixa ~0-3.3V, adequada para os sensores usados aqui
    };
    esp_err_t err = adc_oneshot_config_channel(s_adc1_handle, channel, &chan_cfg);
    if (err == ESP_OK) s_channel_configured[channel] = true;
    return err;
}

esp_err_t soil_sensor_init(void)
{
    adc_oneshot_unit_init_cfg_t init_cfg = {
        .unit_id = ADC_UNIT_1,
    };
    esp_err_t err = adc_oneshot_new_unit(&init_cfg, &s_adc1_handle);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao iniciar ADC1: %s", esp_err_to_name(err));
        return err;
    }

    for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
        ESP_ERROR_CHECK(ensure_channel_configured(PLANT_CHANNELS[i].soil_adc_channel));
    }
    ESP_ERROR_CHECK(ensure_channel_configured(LDR_ADC_CHANNEL));
    return ESP_OK;
}

static int clamp_pct(int value)
{
    if (value < 0) return 0;
    if (value > 100) return 100;
    return value;
}

int soil_sensor_read_pct(adc_channel_t channel)
{
    int raw = 0;
    esp_err_t err = adc_oneshot_read(s_adc1_handle, channel, &raw);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Falha ao ler canal ADC %d: %s", channel, esp_err_to_name(err));
        return -1;
    }

    // Sensor capacitivo: leitura bruta MAIOR = mais seco, MENOR = mais úmido.
    int pct = ((SOIL_RAW_DRY - raw) * 100) / (SOIL_RAW_DRY - SOIL_RAW_WET);
    return clamp_pct(pct);
}

int light_sensor_read_pct(void)
{
    int raw = 0;
    esp_err_t err = adc_oneshot_read(s_adc1_handle, LDR_ADC_CHANNEL, &raw);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Falha ao ler LDR: %s", esp_err_to_name(err));
        return -1;
    }
    // Indicador relativo (0-100); a relação com luminosidade real depende de
    // como o divisor resistivo do LDR foi montado (calibrar se necessário).
    return clamp_pct((raw * 100) / 4095);
}
