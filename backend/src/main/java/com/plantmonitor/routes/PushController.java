package com.plantmonitor.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantmonitor.db.Database;
import com.plantmonitor.service.NotificationService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/** Porte de routes/push.js — chave pública VAPID e inscrição de Web Push. */
public class PushController {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void register(Javalin app) {
        app.get("/api/push/public-key", PushController::publicKey);
        app.post("/api/push/subscribe", PushController::subscribe);
    }

    private static void publicKey(Context ctx) {
        String key = NotificationService.getVapidPublicKey();
        if (key == null) {
            ctx.status(503).json(Map.of("error", "VAPID não configurado no servidor"));
            return;
        }
        ctx.json(Map.of("publicKey", key));
    }

    @SuppressWarnings("unchecked")
    private static void subscribe(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String endpoint = (String) body.get("endpoint");
        Object keys = body.get("keys");
        if (endpoint == null || keys == null) {
            ctx.status(400).json(Map.of("error", "Subscription inválida"));
            return;
        }

        String keysJson;
        try {
            keysJson = mapper.writeValueAsString(keys);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Database.update(
            "INSERT INTO push_subscriptions (endpoint, keys_json) VALUES (?, ?) " +
            "ON CONFLICT(endpoint) DO UPDATE SET keys_json = excluded.keys_json",
            endpoint, keysJson);

        ctx.status(201).json(Map.of("subscribed", true));
    }
}
