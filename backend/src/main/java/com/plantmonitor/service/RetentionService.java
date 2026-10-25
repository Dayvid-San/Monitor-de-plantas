package com.plantmonitor.service;

import com.plantmonitor.db.Database;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Apaga dados antigos para o disco do servidor não encher (uma foto a cada 30 min
 * por câmera e uma leitura por minuto por planta se acumulam rápido).
 * Cada prazo é configurável por env (em dias); 0 desliga a limpeza daquele tipo.
 */
public class RetentionService {
    public static final int DEFAULT_READINGS_DAYS = 90;
    public static final int DEFAULT_PHOTOS_DAYS = 30;
    public static final int DEFAULT_EVENTS_DAYS = 180;
    private static final int CONSUMED_COMMANDS_DAYS = 7;

    public record Result(int readings, int photos, int events, int commands) {}

    public static void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "retention-purge");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(RetentionService::runConfigured, 1, 24 * 60, TimeUnit.MINUTES);
    }

    private static void runConfigured() {
        try {
            Result r = purge(
                envDays("READINGS_RETENTION_DAYS", DEFAULT_READINGS_DAYS),
                envDays("PHOTOS_RETENTION_DAYS", DEFAULT_PHOTOS_DAYS),
                envDays("EVENTS_RETENTION_DAYS", DEFAULT_EVENTS_DAYS));
            System.out.println("[retention] removidos: " + r);
        } catch (Exception e) {
            System.err.println("[retention] erro na limpeza: " + e.getMessage());
        }
    }

    static int envDays(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            System.err.println("[retention] valor inválido em " + name + ": \"" + raw + "\" (usando " + fallback + ")");
            return fallback;
        }
    }

    /** Remove registros (e arquivos de foto) mais velhos que o prazo em dias; 0 = não apaga. */
    public static Result purge(int readingsDays, int photosDays, int eventsDays) {
        int photos = photosDays > 0 ? purgePhotos(photosDays) : 0;
        int readings = readingsDays > 0 ? deleteOlderThan("readings", "ts", readingsDays) : 0;
        int events = eventsDays > 0 ? deleteOlderThan("events", "ts", eventsDays) : 0;
        int commands = deleteConsumedCommands();
        return new Result(readings, photos, events, commands);
    }

    private static int purgePhotos(int days) {
        String cutoff = "-" + days + " days";
        List<Map<String, Object>> old = Database.queryAll(
            "SELECT id, filepath FROM photos WHERE datetime(ts) < datetime('now', ?)", cutoff);
        for (Map<String, Object> photo : old) {
            // filepath é só o nome do arquivo gravado pelo IngestController; getName() evita sair de PHOTOS_DIR
            File file = new File(Database.PHOTOS_DIR, new File((String) photo.get("filepath")).getName());
            if (file.exists() && !file.delete()) {
                System.err.println("[retention] não consegui apagar " + file);
            }
            Database.update("DELETE FROM photos WHERE id = ?", photo.get("id"));
        }
        return old.size();
    }

    private static int deleteOlderThan(String table, String column, int days) {
        String cutoff = "-" + days + " days";
        Map<String, Object> row = Database.queryOne(
            "SELECT COUNT(*) AS n FROM " + table + " WHERE datetime(" + column + ") < datetime('now', ?)", cutoff);
        int n = ((Number) row.get("n")).intValue();
        if (n > 0) {
            Database.update("DELETE FROM " + table + " WHERE datetime(" + column + ") < datetime('now', ?)", cutoff);
        }
        return n;
    }

    private static int deleteConsumedCommands() {
        String cutoff = "-" + CONSUMED_COMMANDS_DAYS + " days";
        Map<String, Object> row = Database.queryOne(
            "SELECT COUNT(*) AS n FROM commands WHERE consumed = 1 AND datetime(created_at) < datetime('now', ?)", cutoff);
        int n = ((Number) row.get("n")).intValue();
        if (n > 0) {
            Database.update("DELETE FROM commands WHERE consumed = 1 AND datetime(created_at) < datetime('now', ?)", cutoff);
        }
        return n;
    }
}
