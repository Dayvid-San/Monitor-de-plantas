#pragma once
#include "esp_err.h"

// Inicializa NVS + netif + wifi station e bloqueia até conectar (ou falhar
// definitivamente após as tentativas de reconexão configuradas).
esp_err_t wifi_connect_start(const char *ssid, const char *password);
