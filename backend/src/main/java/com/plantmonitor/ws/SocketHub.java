package com.plantmonitor.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Substitui o io.emit(...) do Socket.IO: mantém os clientes WebSocket
 * conectados e transmite eventos como {"type": "...", "payload": {...}}.
 * O frontend (frontend/app.js) espera exatamente esse envelope.
 */
public class SocketHub {
    private static final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void register(WsContext ctx) {
        clients.add(ctx);
    }

    public static void unregister(WsContext ctx) {
        clients.remove(ctx);
    }

    public static void broadcast(String type, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("type", type, "payload", payload));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (WsContext ctx : clients) {
            try {
                ctx.send(json);
            } catch (Exception ignored) {
                // cliente pode ter desconectado entre o registro e o envio
            }
        }
    }
}
