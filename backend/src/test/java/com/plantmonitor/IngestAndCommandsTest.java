package com.plantmonitor;

import com.plantmonitor.TestServer.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IngestAndCommandsTest {

    private static Map<String, Object> sensors(String device, int channel, double moisture, boolean pump) {
        return Map.of("device_id", device, "temp_c", 22.5, "humidity_pct", 55.0, "light_pct", 60.0,
            "plants", List.of(Map.of("channel", channel, "moisture_pct", moisture, "pump_active", pump)));
    }

    @Test
    void readingFromRegisteredPlantIsStoredAndShownInList(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            s.createPlant("Samambaia", "esp-1", 0);

            assertEquals(201, s.postJson("/api/ingest/sensors", sensors("esp-1", 0, 41.5, false)).status());

            List<?> plants = (List<?>) s.get("/api/plants").json();
            Map<?, ?> latest = (Map<?, ?>) ((Map<?, ?>) plants.get(0)).get("latest_reading");
            assertEquals(41.5, ((Number) latest.get("moisture_pct")).doubleValue());
            assertEquals(22.5, ((Number) latest.get("temp_c")).doubleValue());
        }
    }

    @Test
    void readingFromUnregisteredChannelIsSilentlyDropped(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            long id = s.createPlant("Jiboia", "esp-1", 0);

            assertEquals(201, s.postJson("/api/ingest/sensors", sensors("esp-1", 7, 30.0, false)).status());

            List<?> history = (List<?>) s.get("/api/plants/" + id + "/history").json();
            assertTrue(history.isEmpty());
        }
    }

    @Test
    void sensorsWithoutDeviceIdIsRejected(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            assertEquals(400, s.postJson("/api/ingest/sensors", Map.of("plants", List.of())).status());
        }
    }

    @Test
    void duplicateDeviceAndChannelIsConflict(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            s.createPlant("A", "esp-1", 0);
            assertEquals(409, s.postJson("/api/plants", Map.of("name", "B", "device_id", "esp-1", "channel", 0)).status());
        }
    }

    @Test
    void waterNowIsDeliveredToDeviceExactlyOnce(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            long id = s.createPlant("Samambaia", "esp-1", 3);

            assertEquals(202, s.postJson("/api/plants/" + id + "/water-now", Map.of()).status());

            List<?> first = (List<?>) s.get("/api/devices/esp-1/commands").json();
            assertEquals(1, first.size());
            assertEquals(3, ((Number) ((Map<?, ?>) first.get(0)).get("channel")).intValue());
            assertEquals("water_now", ((Map<?, ?>) first.get(0)).get("action"));

            assertTrue(((List<?>) s.get("/api/devices/esp-1/commands").json()).isEmpty());
        }
    }

    @Test
    void waterNowForUnknownPlantIs404(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            assertEquals(404, s.postJson("/api/plants/999/water-now", Map.of()).status());
        }
    }

    @Test
    void deviceConfigReflectsDashboardSettings(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            s.postJson("/api/plants", Map.of("name", "Cacto", "device_id", "esp-1", "channel", 1,
                "moisture_min", 15, "moisture_max", 40, "auto_water", false));

            List<?> config = (List<?>) s.get("/api/devices/esp-1/config").json();
            Map<?, ?> c = (Map<?, ?>) config.get(0);
            assertEquals(15, ((Number) c.get("moisture_min")).intValue());
            assertEquals(40, ((Number) c.get("moisture_max")).intValue());
            assertEquals(false, c.get("auto_water"));
        }
    }

    @Test
    void healthyAndWiltedPhotosGetDifferentLabels(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            Response green = s.postPhoto("cam-1", solidJpeg(new Color(40, 160, 50)));
            assertEquals(201, green.status());
            assertEquals("saudavel", ((Map<?, ?>) green.json()).get("health_label"));

            Response brown = s.postPhoto("cam-1", solidJpeg(new Color(170, 140, 30)));
            assertEquals(201, brown.status());
            assertEquals("critico", ((Map<?, ?>) brown.json()).get("health_label"));
        }
    }

    @Test
    void corruptPhotoIsRejected(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, null)) {
            assertEquals(400, s.postPhoto("cam-1", "isto não é uma imagem".getBytes()).status());
        }
    }

    private static byte[] solidJpeg(Color color) throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
