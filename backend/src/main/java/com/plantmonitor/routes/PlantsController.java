package com.plantmonitor.routes;

import com.plantmonitor.db.Database;
import com.plantmonitor.util.JsonUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Porte de routes/plants.js — CRUD de plantas para o dashboard. */
public class PlantsController {
    private static final int MAX_HISTORY_HOURS = 24 * 30; // 30 dias

    public static void register(Javalin app) {
        app.get("/api/plants", PlantsController::list);
        app.post("/api/plants", PlantsController::create);
        app.patch("/api/plants/{id}", PlantsController::update);
        app.delete("/api/plants/{id}", PlantsController::delete);
        app.get("/api/plants/{id}/history", PlantsController::history);
        app.get("/api/plants/{id}/events", PlantsController::events);
    }

    private static void list(Context ctx) {
        List<Map<String, Object>> plants = Database.queryAll("SELECT * FROM plants ORDER BY name");
        for (Map<String, Object> plant : plants) {
            long id = JsonUtil.asLong(plant.get("id"));
            plant.put("latest_reading", Database.queryOne(
                "SELECT * FROM readings WHERE plant_id = ? ORDER BY ts DESC LIMIT 1", id));
            plant.put("latest_photo", Database.queryOne(
                "SELECT * FROM photos WHERE plant_id = ? ORDER BY ts DESC LIMIT 1", id));
        }
        ctx.json(plants);
    }

    @SuppressWarnings("unchecked")
    private static void create(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String name = (String) body.get("name");
        String deviceId = (String) body.get("device_id");
        Integer channel = JsonUtil.asInt(body.get("channel"));

        if (name == null || deviceId == null || channel == null) {
            ctx.status(400).json(Map.of("error", "name, device_id e channel são obrigatórios"));
            return;
        }

        String species = (String) body.get("species");
        int moistureMin = body.get("moisture_min") != null ? JsonUtil.asInt(body.get("moisture_min")) : 30;
        int moistureMax = body.get("moisture_max") != null ? JsonUtil.asInt(body.get("moisture_max")) : 70;
        boolean autoWater = body.get("auto_water") == null || JsonUtil.asBool(body.get("auto_water"));

        long id;
        try {
            id = Database.update(
                "INSERT INTO plants (name, species, device_id, channel, moisture_min, moisture_max, auto_water) VALUES (?, ?, ?, ?, ?, ?, ?)",
                name, species, deviceId, channel, moistureMin, moistureMax, autoWater ? 1 : 0);
        } catch (Database.DbException e) {
            if ("SQLITE_CONSTRAINT_UNIQUE".equals(e.sqliteErrorCode)) {
                ctx.status(409).json(Map.of("error", "Já existe uma planta cadastrada em \"" + deviceId + "\" canal " + channel));
                return;
            }
            throw e;
        }

        ctx.status(201).json(Database.queryOne("SELECT * FROM plants WHERE id = ?", id));
    }

    @SuppressWarnings("unchecked")
    private static void update(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        Map<String, Object> plant = Database.queryOne("SELECT * FROM plants WHERE id = ?", id);
        if (plant == null) {
            ctx.status(404).json(Map.of("error", "Planta não encontrada"));
            return;
        }

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Map<String, Object> updates = new HashMap<>();
        for (String field : new String[]{"name", "species", "moisture_min", "moisture_max", "auto_water"}) {
            if (body.containsKey(field)) {
                Object value = body.get(field);
                updates.put(field, "auto_water".equals(field) ? (JsonUtil.asBool(value) ? 1 : 0) : value);
            }
        }
        if (updates.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Nenhum campo válido para atualizar"));
            return;
        }

        StringBuilder setClause = new StringBuilder();
        Object[] params = new Object[updates.size() + 1];
        int i = 0;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (i > 0) setClause.append(", ");
            setClause.append(entry.getKey()).append(" = ?");
            params[i++] = entry.getValue();
        }
        params[i] = id;

        Database.update("UPDATE plants SET " + setClause + " WHERE id = ?", params);
        ctx.json(Database.queryOne("SELECT * FROM plants WHERE id = ?", id));
    }

    private static void delete(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        Database.update("DELETE FROM plants WHERE id = ?", id);
        ctx.status(204);
    }

    private static void history(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        Double requested = ctx.queryParam("hours") != null ? tryParseDouble(ctx.queryParam("hours")) : null;
        double hours = (requested != null && requested > 0) ? requested : 48;
        hours = Math.min(Math.max(hours, 1), MAX_HISTORY_HOURS);

        List<Map<String, Object>> readings = Database.queryAll(
            "SELECT moisture_pct, temp_c, humidity_pct, light_pct, pump_active, ts FROM readings " +
            "WHERE plant_id = ? AND datetime(ts) >= datetime('now', ?) ORDER BY ts ASC",
            id, "-" + hours + " hours");
        ctx.json(readings);
    }

    private static void events(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        ctx.json(Database.queryAll("SELECT * FROM events WHERE plant_id = ? ORDER BY ts DESC LIMIT 50", id));
    }

    private static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
