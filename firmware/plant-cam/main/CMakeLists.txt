idf_component_register(
  SRCS "main.c" "wifi_connect.c" "upload_client.c"
  INCLUDE_DIRS "."
  REQUIRES esp_wifi esp_netif esp_event nvs_flash esp_http_client esp32-camera
)
