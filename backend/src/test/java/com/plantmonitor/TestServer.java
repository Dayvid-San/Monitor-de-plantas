package com.plantmonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantmonitor.db.Database;
import io.javalin.Javalin;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/** Sobe o backend de verdade numa porta aleatória, com banco num diretório temporário. */
final class TestServer implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();

    final Javalin app;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String base;

    TestServer(Path dataDir, String apiKey) {
        Database.init(dataDir.toString());
        app = App.create(apiKey, null);
        app.start(0);
        base = "http://localhost:" + app.port();
    }

    record Response(int status, String body) {
        Object json() throws Exception {
            return JSON.readValue(body, Object.class);
        }
    }

    Response get(String path, String... headers) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), headers);
    }

    Response postJson(String path, Object body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return send(b, headers);
    }

    Response postPhoto(String cameraId, byte[] jpeg, String... headers) throws Exception {
        String boundary = "----plant" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"camera_id\"\r\n\r\n" + cameraId + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"p.jpg\"\r\n"
            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(jpeg);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/api/ingest/photo"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
        return send(b, headers);
    }

    private Response send(HttpRequest.Builder builder, String... headers) throws Exception {
        for (int i = 0; i + 1 < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(resp.statusCode(), resp.body());
    }

    long createPlant(String name, String deviceId, int channel) throws Exception {
        Response r = postJson("/api/plants", Map.of("name", name, "device_id", deviceId, "channel", channel));
        if (r.status() != 201) throw new IllegalStateException("criar planta falhou: " + r.status() + " " + r.body());
        return ((Number) ((Map<?, ?>) r.json()).get("id")).longValue();
    }

    @Override
    public void close() {
        app.stop();
        Database.close();
    }
}
