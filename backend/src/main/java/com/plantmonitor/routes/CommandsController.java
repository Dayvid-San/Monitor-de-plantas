package com.plantmonitor.routes;

import com.plantmonitor.db.Database;
import com.plantmonitor.util.JsonUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Porte de routes/commands.js — canal de comandos/config entre dashboard e dispositivos. */
public class CommandsController {

    public static void register(Javalin app) {
        app.post("/api/plants/{id}/water-now", CommandsController::waterNow);
        app.get("/api/devices/{deviceId}/commands", CommandsController::commands);
        app.get("/api/devices/{deviceId}/config", CommandsController::config);
    }

    private static void waterNow(Context ctx) {
        long id = Long.parseLong(ctx.pathParam("id"));
        Map<String, Object> plant = Database.queryOne("SELECT * FROM plants WHERE id = ?", id);
        if (plant == null) {
            ctx.status(404).json(Map.of("error", "Planta não encontrada"));
            return;
        }

        Database.update("INSERT INTO commands (device_id, channel, action) VALUES (?, ?, 'water_now')",
            plant.get("device_id"), plant.get("channel"));
        Database.update("INSERT INTO events (plant_id, type, message) VALUES (?, 'manual_water_requested', ?)",
            id, "Rega manual solicitada para \"" + plant.get("name") + "\"");

        ctx.status(202).json(Map.of("queued", true));
    }

    private static void commands(Context ctx) {
        String deviceId = ctx.pathParam("deviceId");
        List<Map<String, Object>> pending = Database.queryAll(
            "SELECT id, channel, action FROM commands WHERE device_id = ? AND consumed = 0 ORDER BY id ASC", deviceId);

        if (!pending.isEmpty()) {
            for (Map<String, Object> c : pending) {
                Database.update("UPDATE commands SET consumed = 1 WHERE id = ?", c.get("id"));
            }
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Map<String, Object> c : pending) {
            response.add(Map.of("channel", c.get("channel"), "action", c.get("action")));
        }
        ctx.json(response);
    }

    private static void config(Context ctx) {
        String deviceId = ctx.pathParam("deviceId");
        List<Map<String, Object>> rows = Database.queryAll(
            "SELECT channel, moisture_min, moisture_max, auto_water FROM plants WHERE device_id = ? ORDER BY channel",
            deviceId);

        List<Map<String, Object>> response = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            response.add(Map.of(
                "channel", row.get("channel"),
                "moisture_min", row.get("moisture_min"),
                "moisture_max", row.get("moisture_max"),
                "auto_water", JsonUtil.asInt(row.get("auto_water")) != null && JsonUtil.asInt(row.get("auto_water")) == 1
            ));
        }
        ctx.json(response);
    }
}
