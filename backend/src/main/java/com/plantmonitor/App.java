package com.plantmonitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.plantmonitor.db.Database;
import com.plantmonitor.routes.CommandsController;
import com.plantmonitor.routes.IngestController;
import com.plantmonitor.routes.PlantsController;
import com.plantmonitor.routes.PushController;
import com.plantmonitor.service.NotificationService;
import com.plantmonitor.service.SchedulerService;
import com.plantmonitor.ws.SocketHub;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class App {

    public static void start() {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));

        Database.init();
        loadVapidKeys();

        Javalin app = Javalin.create(config -> {
            config.http.maxRequestSize = 6L * 1024 * 1024;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "../frontend";
                staticFiles.location = Location.EXTERNAL;
            });
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/photos";
                staticFiles.directory = Database.PHOTOS_DIR;
                staticFiles.location = Location.EXTERNAL;
            });
        });

        app.get("/health", ctx -> ctx.json(Map.of("ok", true)));

        IngestController.register(app);
        PlantsController.register(app);
        CommandsController.register(app);
        PushController.register(app);

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                SocketHub.register(ctx);
                System.out.println("[ws] cliente conectado: " + ctx.sessionId());
            });
            ws.onClose(ctx -> SocketHub.unregister(ctx));
        });

        // Handler de erro central — mesmo papel do middleware de erro da versão Node:
        // erros de negócio conhecidos são tratados na própria rota (ver PlantsController,
        // IngestController); isto aqui é o fallback pro que não foi previsto.
        app.exception(JsonProcessingException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", "JSON inválido no corpo da requisição")));
        app.exception(NumberFormatException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", "Parâmetro numérico inválido")));
        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("[erro não tratado] " + e);
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Erro interno do servidor"));
        });

        SchedulerService.start();

        app.start(port);
        System.out.println("Backend do monitor de plantas rodando em http://localhost:" + port);
    }

    private static void loadVapidKeys() {
        File vapidFile = new File(Database.DATA_DIR, "vapid.json");
        if (!vapidFile.exists()) {
            System.err.println("[app] Chaves VAPID não encontradas (data/vapid.json). Rode 'generate-vapid' para habilitar notificações push.");
            return;
        }
        try {
            Map<?, ?> keys = new com.fasterxml.jackson.databind.ObjectMapper().readValue(vapidFile, Map.class);
            NotificationService.init((String) keys.get("publicKey"), (String) keys.get("privateKey"));
        } catch (IOException e) {
            System.err.println("[app] Falha ao ler data/vapid.json: " + e.getMessage());
        }
    }
}
