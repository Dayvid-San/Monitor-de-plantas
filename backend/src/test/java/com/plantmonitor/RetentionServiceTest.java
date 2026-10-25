package com.plantmonitor;

import com.plantmonitor.db.Database;
import com.plantmonitor.service.RetentionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RetentionServiceTest {
    @BeforeEach
    void setUp(@TempDir Path dir) {
        Database.init(dir.toString());
        Database.update("INSERT INTO plants (id, name, device_id, channel) VALUES (1, 'P', 'esp-1', 0)");
    }

    @AfterEach
    void tearDown() {
        Database.close();
    }

    private static long count(String table) {
        return ((Number) Database.queryOne("SELECT COUNT(*) AS n FROM " + table).get("n")).longValue();
    }

    private static void reading(String ts) {
        Database.update("INSERT INTO readings (plant_id, moisture_pct, ts) VALUES (1, 50, ?)", ts);
    }

    @Test
    void oldReadingsAreRemovedAndRecentOnesKept() {
        reading("2000-01-01 00:00:00");
        reading("2000-06-01 00:00:00");
        Database.update("INSERT INTO readings (plant_id, moisture_pct) VALUES (1, 50)"); // agora

        RetentionService.Result r = RetentionService.purge(90, 0, 0);

        assertEquals(2, r.readings());
        assertEquals(1, count("readings"));
    }

    @Test
    void oldPhotosAreRemovedFromDatabaseAndDisk() throws Exception {
        File oldFile = new File(Database.PHOTOS_DIR, "cam-old.jpg");
        File newFile = new File(Database.PHOTOS_DIR, "cam-new.jpg");
        Files.write(oldFile.toPath(), new byte[]{1});
        Files.write(newFile.toPath(), new byte[]{1});
        Database.update("INSERT INTO photos (camera_id, filepath, ts) VALUES ('cam', 'cam-old.jpg', '2000-01-01 00:00:00')");
        Database.update("INSERT INTO photos (camera_id, filepath) VALUES ('cam', 'cam-new.jpg')");

        RetentionService.Result r = RetentionService.purge(0, 30, 0);

        assertEquals(1, r.photos());
        assertFalse(oldFile.exists());
        assertTrue(newFile.exists());
        assertEquals(1, count("photos"));
    }

    @Test
    void photoRecordWithMissingFileIsStillRemoved() {
        Database.update("INSERT INTO photos (camera_id, filepath, ts) VALUES ('cam', 'sumiu.jpg', '2000-01-01 00:00:00')");
        assertEquals(1, RetentionService.purge(0, 30, 0).photos());
        assertEquals(0, count("photos"));
    }

    @Test
    void photoPathCannotEscapeThePhotosDirectory() throws Exception {
        Path outside = Files.createTempFile("fora", ".jpg");
        try {
            Database.update("INSERT INTO photos (camera_id, filepath, ts) VALUES ('cam', ?, '2000-01-01 00:00:00')",
                "../../" + outside.toAbsolutePath());
            RetentionService.purge(0, 30, 0);
            assertTrue(Files.exists(outside), "arquivo fora de PHOTOS_DIR não pode ser apagado");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void zeroDisablesCleanup() {
        reading("2000-01-01 00:00:00");
        Database.update("INSERT INTO events (type, message, ts) VALUES ('x', 'm', '2000-01-01 00:00:00')");

        RetentionService.Result r = RetentionService.purge(0, 0, 0);

        assertEquals(0, r.readings() + r.photos() + r.events());
        assertEquals(1, count("readings"));
        assertEquals(1, count("events"));
    }

    @Test
    void consumedOldCommandsAreRemovedButPendingOnesStay() {
        Database.update("INSERT INTO commands (device_id, channel, action, consumed, created_at) VALUES ('d', 0, 'water_now', 1, '2000-01-01 00:00:00')");
        Database.update("INSERT INTO commands (device_id, channel, action, consumed, created_at) VALUES ('d', 0, 'water_now', 0, '2000-01-01 00:00:00')");

        assertEquals(1, RetentionService.purge(0, 0, 0).commands());
        assertEquals(1, count("commands"));
    }
}
