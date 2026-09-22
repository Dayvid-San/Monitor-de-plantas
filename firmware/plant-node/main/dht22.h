#pragma once
#include "esp_err.h"
#include "driver/gpio.h"

typedef struct {
    float temperature_c;
    float humidity_pct;
} dht22_reading_t;

// Faz a leitura bit-bang do sensor DHT22 no pino informado.
// Respeite pelo menos 2s entre leituras consecutivas (limite do sensor).
esp_err_t dht22_read(gpio_num_t gpio, dht22_reading_t *out);
