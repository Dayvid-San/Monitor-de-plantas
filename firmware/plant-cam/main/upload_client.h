#pragma once
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

// Envia a foto (JPEG) por multipart/form-data para /api/ingest/photo.
// plant_id < 0 significa "sem vínculo" (campo omitido do formulário).
esp_err_t upload_photo(const char *host, int port, const char *camera_id, int plant_id,
                        const uint8_t *jpeg_buf, size_t jpeg_len);
