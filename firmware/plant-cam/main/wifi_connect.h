#pragma once
#include "esp_err.h"

esp_err_t wifi_connect_start(const char *ssid, const char *password);
