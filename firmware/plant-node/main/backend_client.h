#pragma once
#include <stddef.h>
#include <stdbool.h>
#include "esp_err.h"

typedef struct {
    int channel;
    float moisture_pct; // NAN se a leitura falhou
    bool pump_active;
} plant_reading_t;

typedef struct {
    int channel;
    bool water_now;
} pending_command_t;

typedef struct {
    int channel;
    int moisture_min;
    int moisture_max;
    bool auto_water;
} remote_plant_config_t;

// temp_c / humidity_pct / light_pct: passe NAN quando a leitura não estiver disponível.
esp_err_t backend_send_readings(const char *device_id, float temp_c, float humidity_pct, float light_pct,
                                 const plant_reading_t *readings, size_t count);

esp_err_t backend_fetch_commands(const char *device_id, pending_command_t *out_commands,
                                  size_t max_commands, size_t *out_count);

// Busca no backend os limiares/auto_water atuais configurados no dashboard
// para cada canal deste device_id. Se a chamada falhar (rede fora, backend
// indisponível), o chamador deve manter os valores padrão locais.
esp_err_t backend_fetch_config(const char *device_id, remote_plant_config_t *out_configs,
                                size_t max_configs, size_t *out_count);
