package com.plantmonitor.service;

import com.plantmonitor.db.Database;
import com.plantmonitor.ws.SocketHub;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Porte de services/scheduler.js — alerta de dispositivo offline a cada 5 minutos. */
public class SchedulerService {
    private static final int OFFLINE_THRESHOLD_MINUTES = 30;

    public static void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "offline-device-checker");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(SchedulerService::checkOfflineDevices, 5, 5, TimeUnit.MINUTES);
    }

    private static void checkOfflineDevices() {
        try {
            List<Map<String, Object>> stale = Database.queryAll(
                "SELECT device_id, last_seen FROM devices WHERE offline_notified = 0 AND datetime(last_seen) < datetime('now', ?)",
                "-" + OFFLINE_THRESHOLD_MINUTES + " minutes"
            );

            for (Map<String, Object> device : stale) {
                String deviceId = (String) device.get("device_id");
                String message = "Dispositivo \"" + deviceId + "\" sem enviar dados há mais de " + OFFLINE_THRESHOLD_MINUTES + " minutos.";

                Database.update("INSERT INTO events (plant_id, type, message) VALUES (NULL, 'device_offline', ?)", message);
                Database.update("UPDATE devices SET offline_notified = 1 WHERE device_id = ?", deviceId);

                SocketHub.broadcast("event:new", Map.of("type", "device_offline", "message", message, "device_id", deviceId));
                NotificationService.notifyAll("Dispositivo offline", message, Map.of("device_id", deviceId));
            }
        } catch (Exception e) {
            System.err.println("[scheduler] erro ao checar dispositivos offline: " + e.getMessage());
        }
    }
}
