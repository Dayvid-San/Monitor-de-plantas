#include "pump.h"
#include "plant_config.h"

esp_err_t pump_init_all(void)
{
    for (size_t i = 0; i < PLANT_CHANNEL_COUNT; i++) {
        gpio_config_t cfg = {
            .pin_bit_mask = 1ULL << PLANT_CHANNELS[i].pump_gpio,
            .mode = GPIO_MODE_OUTPUT,
        };
        esp_err_t err = gpio_config(&cfg);
        if (err != ESP_OK) return err;
        gpio_set_level(PLANT_CHANNELS[i].pump_gpio, !PUMP_ACTIVE_LEVEL);
    }
    return ESP_OK;
}

void pump_set(gpio_num_t gpio, bool active)
{
    gpio_set_level(gpio, active ? PUMP_ACTIVE_LEVEL : !PUMP_ACTIVE_LEVEL);
}
