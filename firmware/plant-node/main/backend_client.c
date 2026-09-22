#include "backend_client.h"

#include <math.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "esp_http_client.h"
#include "esp_log.h"
#include "cJSON.h"
#include "sdkconfig.h"

static const char *TAG = "backend_client";

#define RESPONSE_BUF_SIZE 1024
static char s_response_buf[RESPONSE_BUF_SIZE];
static int s_response_len;

static esp_err_t http_event_handler(esp_http_client_event_t *evt)
{
    if (evt->event_id == HTTP_EVENT_ON_DATA) {
        int remaining = RESPONSE_BUF_SIZE - 1 - s_response_len;
        if (remaining > 0) {
            int to_copy = evt->data_len < remaining ? evt->data_len : remaining;
            memcpy(s_response_buf + s_response_len, evt->data, to_copy);
            s_response_len += to_copy;
            s_response_buf[s_response_len] = '\0';
        }
    }
    return ESP_OK;
}

static void build_url(char *out, size_t out_size, const char *path)
{
    snprintf(out, out_size, "http://%s:%d%s", CONFIG_PLANT_BACKEND_HOST, CONFIG_PLANT_BACKEND_PORT, path);
}

esp_err_t backend_send_readings(const char *device_id, float temp_c, float humidity_pct, float light_pct,
                                 const plant_reading_t *readings, size_t count)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "device_id", device_id);

    if (isnan(temp_c)) cJSON_AddNullToObject(root, "temp_c");
    else cJSON_AddNumberToObject(root, "temp_c", temp_c);

    if (isnan(humidity_pct)) cJSON_AddNullToObject(root, "humidity_pct");
    else cJSON_AddNumberToObject(root, "humidity_pct", humidity_pct);

    if (isnan(light_pct)) cJSON_AddNullToObject(root, "light_pct");
    else cJSON_AddNumberToObject(root, "light_pct", light_pct);

    cJSON *plants = cJSON_AddArrayToObject(root, "plants");
    for (size_t i = 0; i < count; i++) {
        cJSON *p = cJSON_CreateObject();
        cJSON_AddNumberToObject(p, "channel", readings[i].channel);
        if (isnan(readings[i].moisture_pct)) cJSON_AddNullToObject(p, "moisture_pct");
        else cJSON_AddNumberToObject(p, "moisture_pct", readings[i].moisture_pct);
        cJSON_AddBoolToObject(p, "pump_active", readings[i].pump_active);
        cJSON_AddItemToArray(plants, p);
    }

    char *json_str = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (!json_str) return ESP_ERR_NO_MEM;

    char url[128];
    build_url(url, sizeof(url), "/api/ingest/sensors");

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = 5000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_post_field(client, json_str, strlen(json_str));

    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        int status = esp_http_client_get_status_code(client);
        if (status >= 300) {
            ESP_LOGW(TAG, "Backend respondeu status %d ao enviar leituras", status);
            err = ESP_FAIL;
        }
    } else {
        ESP_LOGW(TAG, "Falha ao enviar leituras: %s", esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    free(json_str);
    return err;
}

esp_err_t backend_fetch_commands(const char *device_id, pending_command_t *out_commands,
                                  size_t max_commands, size_t *out_count)
{
    *out_count = 0;
    s_response_len = 0;
    s_response_buf[0] = '\0';

    char path[128];
    snprintf(path, sizeof(path), "/api/devices/%s/commands", device_id);
    char url[160];
    build_url(url, sizeof(url), path);

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_GET,
        .timeout_ms = 5000,
        .event_handler = http_event_handler,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_err_t err = esp_http_client_perform(client);
    int status = esp_http_client_get_status_code(client);
    esp_http_client_cleanup(client);

    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Falha ao buscar comandos: %s", esp_err_to_name(err));
        return err;
    }
    if (status >= 300) {
        ESP_LOGW(TAG, "Backend respondeu status %d ao buscar comandos", status);
        return ESP_FAIL;
    }

    cJSON *arr = cJSON_Parse(s_response_buf);
    if (!arr || !cJSON_IsArray(arr)) {
        if (arr) cJSON_Delete(arr);
        return ESP_OK; // sem comandos válidos, não é erro fatal
    }

    int n = cJSON_GetArraySize(arr);
    for (int i = 0; i < n && (size_t)*out_count < max_commands; i++) {
        cJSON *item = cJSON_GetArrayItem(arr, i);
        cJSON *channel = cJSON_GetObjectItem(item, "channel");
        cJSON *action = cJSON_GetObjectItem(item, "action");
        if (!cJSON_IsNumber(channel) || !cJSON_IsString(action)) continue;

        out_commands[*out_count].channel = channel->valueint;
        out_commands[*out_count].water_now = strcmp(action->valuestring, "water_now") == 0;
        (*out_count)++;
    }

    cJSON_Delete(arr);
    return ESP_OK;
}

esp_err_t backend_fetch_config(const char *device_id, remote_plant_config_t *out_configs,
                                size_t max_configs, size_t *out_count)
{
    *out_count = 0;
    s_response_len = 0;
    s_response_buf[0] = '\0';

    char path[128];
    snprintf(path, sizeof(path), "/api/devices/%s/config", device_id);
    char url[160];
    build_url(url, sizeof(url), path);

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_GET,
        .timeout_ms = 5000,
        .event_handler = http_event_handler,
    };
    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_err_t err = esp_http_client_perform(client);
    int status = esp_http_client_get_status_code(client);
    esp_http_client_cleanup(client);

    if (err != ESP_OK || status >= 300) {
        ESP_LOGW(TAG, "Falha ao buscar config remota (err=%s, status=%d)", esp_err_to_name(err), status);
        return ESP_FAIL;
    }

    cJSON *arr = cJSON_Parse(s_response_buf);
    if (!arr || !cJSON_IsArray(arr)) {
        if (arr) cJSON_Delete(arr);
        return ESP_FAIL;
    }

    int n = cJSON_GetArraySize(arr);
    for (int i = 0; i < n && (size_t)*out_count < max_configs; i++) {
        cJSON *item = cJSON_GetArrayItem(arr, i);
        cJSON *channel = cJSON_GetObjectItem(item, "channel");
        cJSON *min = cJSON_GetObjectItem(item, "moisture_min");
        cJSON *max = cJSON_GetObjectItem(item, "moisture_max");
        cJSON *auto_water = cJSON_GetObjectItem(item, "auto_water");
        if (!cJSON_IsNumber(channel) || !cJSON_IsNumber(min) || !cJSON_IsNumber(max)) continue;

        out_configs[*out_count].channel = channel->valueint;
        out_configs[*out_count].moisture_min = min->valueint;
        out_configs[*out_count].moisture_max = max->valueint;
        out_configs[*out_count].auto_water = cJSON_IsTrue(auto_water);
        (*out_count)++;
    }

    cJSON_Delete(arr);
    return ESP_OK;
}
