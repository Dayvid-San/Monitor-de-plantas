package com.plantmonitor.routes;

import com.plantmonitor.db.Database;
import com.plantmonitor.service.ImageAnalysisService;
import com.plantmonitor.service.NotificationService;
import com.plantmonitor.util.JsonUtil;
import com.plantmonitor.ws.SocketHub;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Porte de routes/ingest.js — escrita vinda dos dispositivos (ESP32). */
public class IngestController {
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    public static void register(Javalin app) {
        app.post("/api/ingest/sensors", IngestController::handleSensors);
        app.post("/api/ingest/photo", IngestController::handlePhoto);
    }

    @SuppressWarnings("unchecked")
    private static void handleSensors(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        String deviceId = (String) body.get("device_id");
        Object plantsRaw = body.get("plants");
        if (deviceId == null || !(plantsRaw instanceof List)) {
            ctx.status(400).json(Map.of("error", "device_id e plants[] são obrigatórios"));
            return;
        }
        List<Map<String, Object>> plants = (List<Map<String, Object>>) plantsRaw;

        touchDevice(deviceId);

        Double tempC = JsonUtil.asDouble(body.get("temp_c"));
        Double humidityPct = JsonUtil.asDouble(body.get("humidity_pct"));
        Double lightPct = JsonUtil.asDouble(body.get("light_pct"));

        for (Map<String, Object> p : plants) {
            Integer channel = JsonUtil.asInt(p.get("channel"));
            if (channel == null) continue;

            Map<String, Object> plant = Database.queryOne(
                "SELECT * FROM plants WHERE device_id = ? AND channel = ?", deviceId, channel);
            if (plant == null) continue; // canal ainda não cadastrado no dashboard

            Double moisturePct = JsonUtil.asDouble(p.get("moisture_pct"));
            boolean pumpActive = JsonUtil.asBool(p.get("pump_active"));
            long plantId = JsonUtil.asLong(plant.get("id"));

            Database.update(
                "INSERT INTO readings (plant_id, moisture_pct, temp_c, humidity_pct, light_pct, pump_active) VALUES (?, ?, ?, ?, ?, ?)",
                plantId, moisturePct, tempC, humidityPct, lightPct, pumpActive ? 1 : 0);

            Map<String, Object> readingPayload = new HashMap<>();
            readingPayload.put("plant_id", plantId);
            readingPayload.put("moisture_pct", moisturePct);
            readingPayload.put("temp_c", tempC);
            readingPayload.put("humidity_pct", humidityPct);
            readingPayload.put("light_pct", lightPct);
            readingPayload.put("pump_active", pumpActive);
            readingPayload.put("ts", JsonUtil.nowIso());
            SocketHub.broadcast("reading:new", readingPayload);

            if (pumpActive) {
                checkPumpEffectiveness(plant);
            }
        }

        ctx.status(201).json(Map.of("ok", true));
    }

    // Se a bomba ligou mas, ao longo de várias leituras seguidas, a umidade
    // não mostra sinal de subir, pode ser reservatório vazio/mangueira entupida.
    private static void checkPumpEffectiveness(Map<String, Object> plant) {
        long plantId = JsonUtil.asLong(plant.get("id"));
        List<Map<String, Object>> recent = Database.queryAll(
            "SELECT moisture_pct, pump_active, ts FROM readings WHERE plant_id = ? ORDER BY ts DESC LIMIT 6", plantId);

        boolean allPumping = recent.size() >= 6 && recent.stream()
            .allMatch(r -> JsonUtil.asInt(r.get("pump_active")) != null && JsonUtil.asInt(r.get("pump_active")) == 1);

        boolean notRising = false;
        if (recent.size() >= 6) {
            Double first = JsonUtil.asDouble(recent.get(0).get("moisture_pct"));
            Double last = JsonUtil.asDouble(recent.get(5).get("moisture_pct"));
            if (first != null && last != null) notRising = first <= last + 2;
        }

        if (allPumping && notRising) {
            String plantName = (String) plant.get("name");
            String message = "A bomba de \"" + plantName + "\" está ativa há várias leituras mas a umidade não sobe. Verifique reservatório e mangueira.";

            Map<String, Object> already = Database.queryOne(
                "SELECT 1 as one FROM events WHERE plant_id = ? AND type = 'pump_ineffective' AND datetime(ts) >= datetime('now', '-1 hours')",
                plantId);
            if (already == null) {
                Database.update("INSERT INTO events (plant_id, type, message) VALUES (?, 'pump_ineffective', ?)", plantId, message);
                SocketHub.broadcast("event:new", Map.of("plant_id", plantId, "type", "pump_ineffective", "message", message));
                NotificationService.notifyAll("Atenção: " + plantName, message, Map.of("plant_id", plantId));
            }
        }
    }

    private static void handlePhoto(Context ctx) {
        UploadedFile file = ctx.uploadedFile("photo");
        String cameraId = firstNonNull(ctx.formParam("camera_id"), ctx.queryParam("camera_id"));
        String plantIdStr = firstNonNull(ctx.formParam("plant_id"), ctx.queryParam("plant_id"));

        if (cameraId == null || file == null) {
            ctx.status(400).json(Map.of("error", "camera_id e o arquivo \"photo\" são obrigatórios"));
            return;
        }
        if (file.size() > MAX_PHOTO_BYTES) {
            ctx.status(400).json(Map.of("error", "Erro no upload do arquivo: File too large"));
            return;
        }

        String filename = cameraId + "-" + System.currentTimeMillis() + "-" + JsonUtil.randomHex(4) + ".jpg";
        File dest = new File(Database.PHOTOS_DIR, filename);
        try (FileOutputStream out = new FileOutputStream(dest)) {
            file.content().transferTo(out);
        } catch (IOException e) {
            ctx.status(500).json(Map.of("error", "Erro ao salvar a imagem"));
            return;
        }

        ImageAnalysisService.Result result;
        try {
            result = ImageAnalysisService.analyzePhoto(dest.getPath());
        } catch (IOException e) {
            dest.delete();
            ctx.status(400).json(Map.of("error", "Não foi possível processar a imagem enviada (arquivo inválido ou corrompido)"));
            return;
        }

        Long plantId = plantIdStr != null ? Long.valueOf(plantIdStr) : null;
        long photoId = Database.update(
            "INSERT INTO photos (camera_id, plant_id, filepath, health_score, health_label) VALUES (?, ?, ?, ?, ?)",
            cameraId, plantId, filename, result.healthScore(), result.healthLabel());

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", photoId);
        payload.put("camera_id", cameraId);
        payload.put("plant_id", plantId);
        payload.put("url", "/photos/" + filename);
        payload.put("health_score", result.healthScore());
        payload.put("health_label", result.healthLabel());
        payload.put("ts", JsonUtil.nowIso());
        SocketHub.broadcast("photo:new", payload);

        if ("critico".equals(result.healthLabel())) {
            String plantName = null;
            if (plantId != null) {
                Map<String, Object> plantRow = Database.queryOne("SELECT name FROM plants WHERE id = ?", plantId);
                if (plantRow != null) plantName = (String) plantRow.get("name");
            }
            String message = "Foto da câmera \"" + cameraId + "\"" + (plantName != null ? " (" + plantName + ")" : "")
                + " indica possível problema de saúde da planta.";
            Database.update("INSERT INTO events (plant_id, type, message) VALUES (?, 'health_critical', ?)", plantId, message);

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("plant_id", plantId);
            eventPayload.put("type", "health_critical");
            eventPayload.put("message", message);
            SocketHub.broadcast("event:new", eventPayload);

            Map<String, Object> data = new HashMap<>();
            data.put("plant_id", plantId);
            NotificationService.notifyAll("Possível problema na planta", message, data);
        }

        ctx.status(201).json(payload);
    }

    private static void touchDevice(String deviceId) {
        Database.update(
            "INSERT INTO devices (device_id, last_seen, offline_notified) VALUES (?, datetime('now'), 0) " +
            "ON CONFLICT(device_id) DO UPDATE SET last_seen = datetime('now'), offline_notified = 0",
            deviceId);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
