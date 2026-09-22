#pragma once
#include <stdbool.h>
#include "esp_err.h"
#include "driver/gpio.h"

esp_err_t pump_init_all(void);
void pump_set(gpio_num_t gpio, bool active);
