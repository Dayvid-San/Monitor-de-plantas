#include "dht22.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_rom_sys.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "dht22";

// Espera o pino atingir o nível desejado, com timeout em microssegundos.
// Retorna o tempo decorrido (us) ou -1 se estourou o timeout.
static int wait_for_level(gpio_num_t gpio, int level, int timeout_us)
{
    int64_t start = esp_timer_get_time();
    while (gpio_get_level(gpio) != level) {
        if (esp_timer_get_time() - start > timeout_us) return -1;
    }
    return (int)(esp_timer_get_time() - start);
}

esp_err_t dht22_read(gpio_num_t gpio, dht22_reading_t *out)
{
    uint8_t data[5] = { 0 };

    gpio_config_t out_cfg = {
        .pin_bit_mask = 1ULL << gpio,
        .mode = GPIO_MODE_OUTPUT_OD,
        .pull_up_en = GPIO_PULLUP_ENABLE,
    };
    gpio_config(&out_cfg);

    // Sinal de start: puxa a linha para baixo por ~3ms, depois libera (pull-up externo/interno sobe a linha).
    gpio_set_level(gpio, 0);
    vTaskDelay(pdMS_TO_TICKS(3));
    gpio_set_level(gpio, 1);
    esp_rom_delay_us(30);

    gpio_config_t in_cfg = {
        .pin_bit_mask = 1ULL << gpio,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
    };
    gpio_config(&in_cfg);

    // Resposta do sensor: ~80us em nível baixo, depois ~80us em nível alto.
    portDISABLE_INTERRUPTS();
    if (wait_for_level(gpio, 0, 100) < 0) { portENABLE_INTERRUPTS(); ESP_LOGW(TAG, "sem resposta (low)"); return ESP_ERR_TIMEOUT; }
    if (wait_for_level(gpio, 1, 100) < 0) { portENABLE_INTERRUPTS(); ESP_LOGW(TAG, "sem resposta (high)"); return ESP_ERR_TIMEOUT; }
    if (wait_for_level(gpio, 0, 100) < 0) { portENABLE_INTERRUPTS(); ESP_LOGW(TAG, "sem início de dados"); return ESP_ERR_TIMEOUT; }

    for (int bit = 0; bit < 40; bit++) {
        if (wait_for_level(gpio, 1, 80) < 0) { portENABLE_INTERRUPTS(); ESP_LOGW(TAG, "timeout bit %d (subida)", bit); return ESP_ERR_TIMEOUT; }
        int high_us = wait_for_level(gpio, 0, 100);
        if (high_us < 0) { portENABLE_INTERRUPTS(); ESP_LOGW(TAG, "timeout bit %d (descida)", bit); return ESP_ERR_TIMEOUT; }

        // ~26-28us de nível alto = bit 0; ~70us = bit 1.
        uint8_t bit_value = high_us > 45 ? 1 : 0;
        data[bit / 8] = (data[bit / 8] << 1) | bit_value;
    }
    portENABLE_INTERRUPTS();

    uint8_t checksum = (data[0] + data[1] + data[2] + data[3]) & 0xFF;
    if (checksum != data[4]) {
        ESP_LOGW(TAG, "checksum inválido (esperado %02x, recebido %02x)", checksum, data[4]);
        return ESP_ERR_INVALID_CRC;
    }

    out->humidity_pct = ((data[0] << 8) | data[1]) / 10.0f;
    int16_t raw_temp = ((data[2] & 0x7F) << 8) | data[3];
    out->temperature_c = raw_temp / 10.0f;
    if (data[2] & 0x80) out->temperature_c = -out->temperature_c;

    return ESP_OK;
}
