#pragma once
#include "esp_err.h"

// Inicializa NVS + netif + wifi station e bloqueia até conectar, por no máximo
// ~30 s. Retorna ESP_OK se conectou, ou ESP_ERR_TIMEOUT se ainda sem rede — nesse
// caso a reconexão continua em segundo plano, sem limite de tentativas, e o
// chamador deve seguir operando offline em vez de abortar.
esp_err_t wifi_connect_start(const char *ssid, const char *password);
