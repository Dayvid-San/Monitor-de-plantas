package com.plantmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Porte de scripts/simulate-device.js — simula um ESP32 real: cadastra (se
 * preciso) duas plantas de teste e passa a enviar leituras periódicas +
 * responder a comandos de rega manual. Uso: java -jar app.jar simulate
 * (com o backend já rodando em outro terminal).
 */
public class Simulator {
    private static final String BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:3000");
    private static final String DEVICE_ID = "esp32-sim-1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static class ChannelState {
        final int channel;
        final String name;
        final String species;
        double moisture;
        boolean justWatered;

        ChannelState(int channel, String name, String species, double moisture) {
            this.channel = channel;
            this.name = name;
            this.species = species;
            this.moisture = moisture;
        }
    }

    private static final List<ChannelState> channels = List.of(
        new ChannelState(0, "Samambaia (simulada)", "Nephrolepis exaltata", 55),
        new ChannelState(1, "Jiboia (simulada)", "Epipremnum aureum", 40)
    );

    public static void run() throws Exception {
        System.out.println("Simulando dispositivo \"" + DEVICE_ID + "\" contra " + BASE_URL);
        ensurePlantsExist();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        executor.scheduleAtFixedRate(Simulator::safeTick, 3, 3, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(Simulator::safeSendReadings, 0, 5, TimeUnit.SECONDS);

        new java.util.concurrent.CountDownLatch(1).await();
    }

    private static void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            System.err.println("Erro no poll de comandos: " + e.getMessage());
        }
    }

    private static void safeSendReadings() {
        try {
            sendReadings();
        } catch (Exception e) {
            System.err.println("Erro ao enviar leitura: " + e.getMessage());
        }
    }

    private static void ensurePlantsExist() throws Exception {
        JsonNode existing = apiGet("/api/plants");
        for (ChannelState ch : channels) {
            boolean found = false;
            for (JsonNode p : existing) {
                if (DEVICE_ID.equals(p.path("device_id").asText())
                    && p.path("channel").asInt() == ch.channel) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("Criando planta de teste \"" + ch.name + "\" (canal " + ch.channel + ")...");
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", ch.name);
                body.put("species", ch.species);
                body.put("device_id", DEVICE_ID);
                body.put("channel", ch.channel);
                body.put("moisture_min", 30);
                body.put("moisture_max", 65);
                body.put("auto_water", true);
                apiPost("/api/plants", body);
            }
        }
    }

    private static void tick() throws Exception {
        JsonNode commands = apiGet("/api/devices/" + DEVICE_ID + "/commands");
        for (JsonNode cmd : commands) {
            if ("water_now".equals(cmd.path("action").asText())) {
                int channel = cmd.path("channel").asInt();
                for (ChannelState ch : channels) {
                    if (ch.channel == channel) {
                        System.out.println("[comando] Regando manualmente canal " + ch.channel + " (" + ch.name + ")");
                        ch.moisture = Math.min(70, ch.moisture + 20);
                        ch.justWatered = true;
                    }
                }
            }
        }
    }

    private static void sendReadings() throws Exception {
        List<Map<String, Object>> plants = new ArrayList<>();
        for (ChannelState ch : channels) {
            ch.moisture = jitter(ch.moisture - 0.6, 1.5);
            boolean pumpActive = false;
            if (ch.moisture < 30) {
                pumpActive = true;
                ch.moisture += 15;
            }
            ch.moisture = Math.max(5, Math.min(95, ch.moisture));
            boolean reportedPumpActive = pumpActive || ch.justWatered;
            ch.justWatered = false;

            Map<String, Object> plantPayload = new LinkedHashMap<>();
            plantPayload.put("channel", ch.channel);
            plantPayload.put("moisture_pct", Math.round(ch.moisture * 10) / 10.0);
            plantPayload.put("pump_active", reportedPumpActive);
            plants.add(plantPayload);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", DEVICE_ID);
        payload.put("temp_c", Math.round(jitter(23, 1.5) * 10) / 10.0);
        payload.put("humidity_pct", Math.round(jitter(55, 5)));
        payload.put("light_pct", Math.round(jitter(60, 15)));
        payload.put("plants", plants);

        apiPost("/api/ingest/sensors", payload);

        StringBuilder log = new StringBuilder();
        for (Map<String, Object> p : plants) {
            if (log.length() > 0) log.append(" | ");
            log.append("canal ").append(p.get("channel")).append(": ").append(p.get("moisture_pct")).append("%");
            if ((Boolean) p.get("pump_active")) log.append(" (bombeando)");
        }
        System.out.println("[leitura] " + log);
    }

    private static double jitter(double value, double amount) {
        return value + (RANDOM.nextDouble() * 2 - 1) * amount;
    }

    private static JsonNode apiGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException(path + " -> HTTP " + resp.statusCode());
        return mapper.readTree(resp.body());
    }

    private static JsonNode apiPost(String path, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException(path + " -> HTTP " + resp.statusCode());
        return resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
    }
}
