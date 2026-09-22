#pragma once
#include "esp_err.h"
#include "esp_adc/adc_oneshot.h"

esp_err_t soil_sensor_init(void);

// Retorna umidade estimada em % (0-100), ou -1 em erro de leitura.
int soil_sensor_read_pct(adc_channel_t channel);

// Retorna luminosidade relativa em % (0-100, quanto maior mais claro), ou -1 em erro.
int light_sensor_read_pct(void);
