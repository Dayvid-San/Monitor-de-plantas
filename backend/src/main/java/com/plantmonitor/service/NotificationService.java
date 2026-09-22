package com.plantmonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantmonitor.db.Database;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Envio de Web Push (VAPID). Porte de services/notifications.js. Se as
 * chaves VAPID não estiverem configuradas, notifyAll() vira no-op silencioso
 * (mesmo comportamento da versão Node).
 */
public class NotificationService {
    private static PushService pushService;
    private static String vapidPublicKey;
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void init(String publicKey, String privateKey) {
        try {
            pushService = new PushService(publicKey, privateKey, "mailto:admin@example.com");
            vapidPublicKey = publicKey;
        } catch (Exception e) {
            System.err.println("[notifications] Chaves VAPID inválidas, notificações desabilitadas: " + e.getMessage());
        }
    }

    public static String getVapidPublicKey() {
        return vapidPublicKey;
    }

    @SuppressWarnings("unchecked")
    public static void notifyAll(String title, String body, Map<String, Object> data) {
        if (pushService == null) return;

        List<Map<String, Object>> subs = Database.queryAll("SELECT id, endpoint, keys_json FROM push_subscriptions");
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                "title", title,
                "body", body,
                "data", data == null ? Map.of() : data
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Map<String, Object> sub : subs) {
            try {
                Map<String, String> keys = mapper.readValue((String) sub.get("keys_json"), Map.class);
                Subscription.Keys subKeys = new Subscription.Keys(keys.get("p256dh"), keys.get("auth"));
                Subscription subscription = new Subscription((String) sub.get("endpoint"), subKeys);
                Notification notification = new Notification(subscription, payload);

                HttpResponse response = pushService.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    Database.update("DELETE FROM push_subscriptions WHERE id = ?", sub.get("id"));
                } else if (status >= 300) {
                    System.err.println("[notifications] push respondeu status " + status + " para endpoint " + sub.get("endpoint"));
                }
            } catch (Exception e) {
                System.err.println("[notifications] Falha ao enviar push: " + e.getMessage());
            }
        }
    }
}
