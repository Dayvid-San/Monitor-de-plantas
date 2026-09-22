# Contexto: Backend (`backend/`)

Carregue este contexto para tarefas em `backend/` — API HTTP, banco de dados, análise de fotos, notificações, agendamento.

## Stack

Java 17 + Maven, servidor web **Javalin 6** (embutido sobre Jetty), `sqlite-jdbc` (síncrono, WAL), WebSocket nativo do Javalin (não Socket.IO), `nl.martijndwars:web-push` + BouncyCastle para Web Push/VAPID, Jackson para JSON. Sem ORM, sem framework de testes configurado, sem linter configurado. Empacotado como um único "fat jar" via `maven-shade-plugin` (`target/plant-monitor-backend.jar`).

> Este backend já foi reescrito uma vez: era Node.js/Express, migrado para Java a pedido do usuário. `doc/` e outros arquivos de `doc_agents/` já refletem a versão Java; não espere encontrar `package.json`/`node_modules` aqui.

## Mapa de arquivos

- `pom.xml` — dependências e o plugin de shade (fat jar).
- `src/main/resources/schema.sql` — única fonte de verdade do esquema (aplicado no boot). Ver seção Modelo de dados.
- `src/main/java/com/plantmonitor/Main.java` — ponto de entrada; despacha por `args[0]` (`start` é o padrão): `App.start()`, `Simulator.run()` ou `GenerateVapidKeys.run()`.
- `App.java` — monta o Javalin: config (CORS liberado, tamanho máx. de requisição, arquivos estáticos de `../frontend` e `data/photos`), registra os controllers, o endpoint WebSocket (`/ws`), os exception handlers centrais, e sobe o agendador.
- `db/Database.java` — conexão JDBC única e síncrona; helpers `queryAll`/`queryOne`/`update` (equivalentes a `.all()/.get()/.run()` do better-sqlite3 da versão antiga). `DbException` expõe `sqliteErrorCode` (hoje só distingue `SQLITE_CONSTRAINT_UNIQUE` do resto) para as rotas tratarem erros de negócio conhecidos.
- `util/JsonUtil.java` — conversões seguras de `Object` vindo de JSON (`asInt`/`asDouble`/`asLong`/`asBool`), `nowIso()` (timestamp UTC em milissegundos terminando em `Z`) e `randomHex()`.
- `ws/SocketHub.java` — substitui o `io.emit(...)` do Socket.IO: mantém os `WsContext` conectados e manda `{"type": "...", "payload": {...}}` para todos.
- `routes/IngestController.java` — escrita vinda dos dispositivos: `POST /api/ingest/sensors`, `POST /api/ingest/photo`.
- `routes/CommandsController.java` — leitura pelos dispositivos (`GET /api/devices/:deviceId/commands`, `GET /api/devices/:deviceId/config`) e o gatilho de rega manual usado pelo dashboard (`POST /api/plants/:id/water-now`).
- `routes/PlantsController.java` — CRUD de plantas para o dashboard + `/history` + `/events`.
- `routes/PushController.java` — chave pública VAPID + inscrição de push.
- `service/ImageAnalysisService.java` — heurística de cor (ver seção própria).
- `service/NotificationService.java` — envio de Web Push; `init(publicKey, privateKey)` é chamado por `App` a partir de `data/vapid.json` — se o arquivo não existir, fica sem inicializar e `notifyAll()` vira no-op silencioso.
- `service/SchedulerService.java` — `ScheduledExecutorService` a cada 5 min, marca dispositivos sem enviar dados há mais de 30 min (`devices.offline_notified`) e dispara evento/push uma única vez por período offline.
- `GenerateVapidKeys.java` — gera `data/vapid.json` uma vez (não sobrescreve se já existir); usa `KeyPairGenerator("EC", "BC")` + `nl.martijndwars.webpush.Utils.encode(...)` para o formato de chave que `PushService` espera.
- `Simulator.java` — fake ESP32 completo (leituras + resposta a comandos) usando `java.net.http.HttpClient`, útil para testar sem hardware. Ver `java -jar ... simulate` no `CLAUDE.md` raiz.
- `data/` — tudo gitignored e gerado em runtime: `plants.db`, `photos/*.jpg`, `vapid.json`.

## Convenções

- Todo acesso a banco passa por `Database.queryAll/queryOne/update` — não introduza um ORM nem um pool de conexões; é um projeto doméstico, uma única `Connection` compartilhada é suficiente (SQLite é single-writer de qualquer forma).
- Para transmitir eventos ao dashboard dentro de uma rota, use `SocketHub.broadcast(type, payload)` — nunca monte a mensagem WebSocket manualmente em outro lugar.
- Timestamps gravados via `datetime('now')` no schema ficam em UTC, formato `"YYYY-MM-DD HH:MM:SS"` (sem `T`/`Z`). Quando um payload de WebSocket é montado manualmente (ex. em `IngestController`), use `JsonUtil.nowIso()` — o frontend trata os dois formatos (ver `doc_agents/frontend.md`, gotcha de timestamp).
- Erros esperados de negócio (ex. constraint `UNIQUE` violada) devem ser capturados na própria rota e responder um status/mensagem específicos (ver `POST /api/plants` abaixo) — os `app.exception(...)` registrados em `App.java` são o *fallback* para o que não foi previsto (JSON malformado → 400, qualquer outra exceção → 500), não o lugar para tratar casos conhecidos.
- `Map.of(...)` do Java **lança `NullPointerException` se algum valor for `null`** (ex. `plant_id` opcional) — use `HashMap` nesses casos, como já feito em `IngestController`.

## Contratos das rotas

### `POST /api/ingest/sensors`
Body: `{ device_id, temp_c, humidity_pct, light_pct, plants: [{ channel, moisture_pct, pump_active }] }` (os três campos de ambiente e `moisture_pct` aceitam `null`). Para cada item de `plants`, busca a planta por `(device_id, channel)`; **se não achar, ignora silenciosamente** esse item (não é erro — permite que o dispositivo rode com canais ainda não cadastrados no dashboard). Grava em `readings`, transmite `reading:new`, e se `pump_active` for true roda `checkPumpEffectiveness()` (dispara evento `pump_ineffective` + push se a bomba esteve ativa nas últimas 6 leituras seguidas sem a umidade subir — não dispara para regas isoladas/manuais).

### `POST /api/ingest/photo`
Multipart (limite 5MB checado manualmente via `UploadedFile.size()` — acima disso responde `400` direto, sem depender de exceção do Jetty). Campos: `camera_id` (obrigatório, aceito via form field ou query param), `plant_id` (opcional, **não validado contra a tabela `plants`** — confia no valor configurado no firmware). Arquivo no campo `photo`. Salva em `data/photos/<camera_id>-<timestamp>-<random>.jpg`, roda `ImageAnalysisService.analyzePhoto()` — se a imagem for inválida/corrompida (`ImageIO.read` retorna `null`), o arquivo salvo é apagado e a rota responde `400` em vez de deixar a exceção subir. Se a análise for bem-sucedida, grava em `photos`, transmite `photo:new`, e se `health_label` for `"critico"` grava evento `health_critical` + push.

### `GET /api/devices/:deviceId/commands`
Retorna comandos pendentes (`action`, `channel`) para aquele `device_id` e **marca todos como consumidos nessa mesma chamada** — não há confirmação de execução vindo do dispositivo, é fire-and-forget.

### `GET /api/devices/:deviceId/config`
Retorna, por canal, `moisture_min`/`moisture_max`/`auto_water` atuais da tabela `plants` — é assim que o toggle "Auto" e a edição de limiares do dashboard chegam ao ESP32 (ver `doc_agents/firmware-plant-node.md`).

### `POST /api/plants/:id/water-now`
Só enfileira uma linha em `commands`; não fala com o dispositivo diretamente (não há conexão persistente com o ESP32).

### `PlantsController`
CRUD padrão + `GET /:id/history?hours=N` (default 48, **limitado a `MAX_HISTORY_HOURS` = 720 horas/30 dias** — valores maiores, negativos ou não numéricos são normalizados em vez de dar erro) + `GET /:id/events` (últimos 50). `POST /` com `(device_id, channel)` já existente responde `409` com mensagem clara (captura `Database.DbException` com `sqliteErrorCode == "SQLITE_CONSTRAINT_UNIQUE"`) em vez de deixar o erro cru subir. `DELETE /:id` — como `readings`/`events` têm `ON DELETE CASCADE` e `photos.plant_id` tem `ON DELETE SET NULL`, apagar uma planta apaga seu histórico de leituras/eventos mas **as fotos permanecem no disco e no banco**, só perdem o vínculo.

## Modelo de dados (`src/main/resources/schema.sql`)

- `plants` — identidade real é `(device_id, channel)` (UNIQUE constraint), não o nome.
- `readings` — série temporal por `plant_id`.
- `photos` — por `camera_id`, com `plant_id` opcional.
- `events` — log usado tanto para exibir no dashboard quanto como histórico de quando um push foi disparado.
- `commands` — fila de comandos pendentes por `(device_id, channel)`, consumidos uma vez.
- `push_subscriptions` — assinaturas Web Push; entradas cujo envio retorna 404/410 são removidas automaticamente em `NotificationService`.
- `devices` — só rastreia `last_seen`/`offline_notified` para o alerta de dispositivo offline.

## Testar sem hardware

```bash
mvn package
java -jar target/plant-monitor-backend.jar            # terminal 1
java -jar target/plant-monitor-backend.jar simulate    # terminal 2 — cria 2 plantas de teste e gera tráfego
```
