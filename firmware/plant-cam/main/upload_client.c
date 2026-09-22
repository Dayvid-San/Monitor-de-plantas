#include "upload_client.h"

#include <stdio.h>
#include <string.h>
#include "esp_http_client.h"
#include "esp_log.h"

static const char *TAG = "upload_client";
#define BOUNDARY "----plantcamboundary7d81b3"

esp_err_t upload_photo(const char *host, int port, const char *camera_id, int plant_id,
                        const uint8_t *jpeg_buf, size_t jpeg_len)
{
    char url[128];
    snprintf(url, sizeof(url), "http://%s:%d/api/ingest/photo", host, port);

    char header[512];
    int header_len;
    if (plant_id >= 0) {
        header_len = snprintf(
            header, sizeof(header),
            "--" BOUNDARY "\r\n"
            "Content-Disposition: form-data; name=\"camera_id\"\r\n\r\n%s\r\n"
            "--" BOUNDARY "\r\n"
            "Content-Disposition: form-data; name=\"plant_id\"\r\n\r\n%d\r\n"
            "--" BOUNDARY "\r\n"
            "Content-Disposition: form-data; name=\"photo\"; filename=\"capture.jpg\"\r\n"
            "Content-Type: image/jpeg\r\n\r\n",
            camera_id, plant_id);
    } else {
        header_len = snprintf(
            header, sizeof(header),
            "--" BOUNDARY "\r\n"
            "Content-Disposition: form-data; name=\"camera_id\"\r\n\r\n%s\r\n"
            "--" BOUNDARY "\r\n"
            "Content-Disposition: form-data; name=\"photo\"; filename=\"capture.jpg\"\r\n"
            "Content-Type: image/jpeg\r\n\r\n",
            camera_id);
    }

    static const char footer[] = "\r\n--" BOUNDARY "--\r\n";
    int footer_len = sizeof(footer) - 1;
    int content_length = header_len + (int)jpeg_len + footer_len;

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = 15000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_http_client_set_header(client, "Content-Type", "multipart/form-data; boundary=" BOUNDARY);

    esp_err_t err = esp_http_client_open(client, content_length);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao abrir conexão HTTP: %s", esp_err_to_name(err));
        esp_http_client_cleanup(client);
        return err;
    }

    esp_http_client_write(client, header, header_len);
    esp_http_client_write(client, (const char *)jpeg_buf, (int)jpeg_len);
    esp_http_client_write(client, footer, footer_len);

    int status = -1;
    if (esp_http_client_fetch_headers(client) >= 0) {
        status = esp_http_client_get_status_code(client);
    }
    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    if (status < 200 || status >= 300) {
        ESP_LOGW(TAG, "Backend respondeu status %d ao enviar foto", status);
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "Foto enviada (%d bytes)", (int)jpeg_len);
    return ESP_OK;
}
