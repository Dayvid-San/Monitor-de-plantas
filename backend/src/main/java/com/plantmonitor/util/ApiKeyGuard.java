package com.plantmonitor.util;

import io.javalin.Javalin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Chave de API opcional para os endpoints usados pelos dispositivos
 * (/api/ingest/* e /api/devices/*). Se a env DEVICE_API_KEY estiver vazia,
 * nada é exigido (comportamento anterior); se estiver definida, o ESP32
 * precisa mandar o mesmo valor no header X-API-Key (Kconfig PLANT_API_KEY).
 */
public class ApiKeyGuard {
    public static final String HEADER = "X-API-Key";

    public static void register(Javalin app, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return;
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        app.before("/api/ingest/*", ctx -> check(ctx, expected));
        app.before("/api/devices/*", ctx -> check(ctx, expected));
    }

    private static void check(io.javalin.http.Context ctx, byte[] expected) {
        String provided = ctx.header(HEADER);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        // comparação em tempo constante, para não vazar a chave por timing
        if (!MessageDigest.isEqual(expected, actual)) {
            ctx.status(401).json(Map.of("error", "Chave de API ausente ou inválida"));
            ctx.skipRemainingHandlers();
        }
    }
}
