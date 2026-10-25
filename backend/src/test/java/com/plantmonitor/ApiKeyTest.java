package com.plantmonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyTest {
    private static final Map<String, Object> BODY = Map.of("device_id", "esp-1", "plants", List.of());

    @Test
    void deviceEndpointsRequireTheKeyWhenConfigured(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, "segredo")) {
            assertEquals(401, s.postJson("/api/ingest/sensors", BODY).status());
            assertEquals(401, s.postJson("/api/ingest/sensors", BODY, "X-API-Key", "errada").status());
            assertEquals(401, s.get("/api/devices/esp-1/commands").status());
            assertEquals(401, s.get("/api/devices/esp-1/config").status());

            assertEquals(201, s.postJson("/api/ingest/sensors", BODY, "X-API-Key", "segredo").status());
            assertEquals(200, s.get("/api/devices/esp-1/commands", "X-API-Key", "segredo").status());
        }
    }

    @Test
    void dashboardEndpointsStayOpen(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, "segredo")) {
            assertEquals(200, s.get("/api/plants").status());
            assertEquals(200, s.get("/health").status());
        }
    }

    @Test
    void noKeyConfiguredMeansNoRestriction(@TempDir Path dir) throws Exception {
        try (TestServer s = new TestServer(dir, "")) {
            assertEquals(201, s.postJson("/api/ingest/sensors", BODY).status());
            assertEquals(200, s.get("/api/devices/esp-1/commands").status());
        }
    }
}
