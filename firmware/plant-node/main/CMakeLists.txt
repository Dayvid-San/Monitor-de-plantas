idf_component_register(
  SRCS "main.c" "wifi_connect.c" "dht22.c" "soil_sensor.c" "pump.c" "backend_client.c"
  INCLUDE_DIRS "."
  REQUIRES esp_wifi esp_netif esp_event nvs_flash esp_http_client json driver esp_adc
)
