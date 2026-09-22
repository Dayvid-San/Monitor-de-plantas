# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Home plant monitoring + auto-watering system. Three independently-deployed pieces communicate over plain HTTP/JSON (and one raw WebSocket) on the home LAN (no message broker):

- `firmware/plant-node` — ESP-IDF (C) firmware for an ESP32 that reads soil moisture / DHT22 / LDR sensors for several plants and drives a relay per plant to water them.
- `firmware/plant-cam` — ESP-IDF (C) firmware for an ESP32-CAM that takes periodic photos and uploads them.
- `backend/` — Java/Maven server (Javalin): ingests sensor data and photos, stores history in SQLite, runs a color-heuristic "plant health" check on photos, pushes live updates to the dashboard over a raw WebSocket, and sends Web Push notifications.
- `frontend/` — a plain HTML/CSS/JS PWA dashboard, served as static files directly by the backend.

## Commands

### Backend (`backend/`)

```bash
mvn package                                        # builds target/plant-monitor-backend.jar (fat jar, maven-shade-plugin)
java -jar target/plant-monitor-backend.jar          # starts the server, http://localhost:3000 (override with PORT env var)
java -jar target/plant-monitor-backend.jar simulate # fakes an ESP32 (device_id "esp32-sim-1"), auto-creates 2 test
                                                     # plants on channels 0/1 and posts readings — use this to exercise
                                                     # the whole pipeline without any hardware
java -jar target/plant-monitor-backend.jar generate-vapid  # one-time: writes backend/data/vapid.json (VAPID keypair).
                                                            # Without it, NotificationService logs a warning and push is disabled.
```

`Main.java` dispatches on `args[0]` (`start` is the default if omitted) — `App`, `Simulator`, `GenerateVapidKeys` are the three entry points, all bundled into the same jar. There is no test suite or linter configured in this project yet.

### Firmware (`firmware/plant-node`, `firmware/plant-cam`)

Requires ESP-IDF v5.x exported into the shell (`. $IDF_PATH/export.sh` or equivalent) — not available in every environment, so firmware changes may need to be validated by careful reading rather than a build.

```bash
idf.py set-target esp32
idf.py menuconfig   # project-specific Kconfig.projbuild menu: "Plant Node Configuration"
                     # or "Plant Cam Configuration" — Wi-Fi, device_id/camera_id, backend
                     # host:port, read/poll intervals
idf.py build
idf.py -p /dev/ttyUSB0 flash monitor
```

- `plant-node` will not build until `main/plant_config.h` exists — copy it from the checked-in `main/plant_config.example.h`. It defines the per-channel pin mapping (`PLANT_CHANNELS[]`), soil-sensor ADC calibration constants, and relay polarity (`PUMP_ACTIVE_LEVEL`); it's gitignored because it's hardware-specific.
- `plant-cam` depends on the `espressif/esp32-camera` component pulled by the ESP-IDF component manager (declared in `main/idf_component.yml`) — the first build needs internet access to fetch it into `managed_components/`.
- Each firmware project's `build/`, `sdkconfig`, and `managed_components/` are generated/local and gitignored.

### Frontend (`frontend/`)

No build step — plain static HTML/CSS/JS, served by the backend via Javalin's static file handler (`Location.EXTERNAL`, pointed at `../frontend`). Edit and reload the browser; no compiler/watcher involved. `Chart.js` is vendored directly at `frontend/vendor/chart.umd.js` (checked into the repo, not fetched from a CDN or `node_modules`) so the dashboard works on the home LAN without internet — if it's ever upgraded, replace that file with a newer UMD build.

## Architecture

```
ESP32 (plant-node)    --HTTP POST readings-->     backend (Java/Javalin)   --WebSocket-->  dashboard (PWA)
                       <--HTTP GET commands/config--                        --Web Push-->  phone (even if closed)
ESP32-CAM (plant-cam) --HTTP POST photo (multipart)--> backend
                                                          --sqlite-jdbc--> backend/data/plants.db
```

Decisions baked into the code that matter for making consistent changes:

1. **Auto-watering logic lives on the ESP32, not the backend.** `firmware/plant-node/main/main.c` decides when to open a relay from its own in-memory `s_effective[]` thresholds. The backend never issues an "auto water" command — this is deliberate, so watering keeps working if the backend is down. The dashboard's per-plant "auto" toggle and threshold editor only take effect once the device polls `GET /api/devices/:device_id/config` (`CommandsController`), so there's up to `CONFIG_PLANT_READ_INTERVAL_SEC` (default 60s) of lag between a dashboard change and device behavior. The values in `plant_config.h` are just the offline fallback.

2. **`(device_id, channel)` is the join key everywhere.** A row in the `plants` table is identified by `device_id` (matches `CONFIG_PLANT_DEVICE_ID` set via `idf.py menuconfig`) + `channel` (matches an entry's `.channel` in that device's `PLANT_CHANNELS[]`). `IngestController.handleSensors` silently drops any incoming reading whose `(device_id, channel)` doesn't match a registered plant — that's the first thing to check if "a plant isn't showing data."

3. **Manual watering is command-queued, not a direct call.** `POST /api/plants/:id/water-now` only inserts a row into the `commands` table; the ESP32 finds out on its next short-poll of `GET /api/devices/:id/commands` (default every `CONFIG_PLANT_COMMAND_POLL_INTERVAL_SEC` = 8s) and the command is marked consumed on that first fetch — fire-and-forget, no ack path back from the device.

4. **Image "health" is a color heuristic, not ML.** `ImageAnalysisService` decodes with `ImageIO`/`BufferedImage` and buckets pixels by hue (green vs. yellow/brown) into `health_score`/`health_label`. It's deliberately the only place that knows this — swapping in a real model later shouldn't require touching `IngestController` or the schema.

5. **Photo upload from the camera is streamed multipart, not buffered whole.** `firmware/plant-cam/main/upload_client.c` writes the multipart header, then the raw JPEG bytes, then the footer as three separate `esp_http_client_write()` calls instead of concatenating into one buffer — avoids doubling RAM usage on top of the camera framebuffer.

6. **Real-time transport is a raw WebSocket, not Socket.IO.** The dashboard originally used the Socket.IO protocol; that was dropped when the backend moved to Java (keeping protocol parity would have required a separate library — `netty-socketio` — running its own server on a second port). `SocketHub` (backend) and the `connectWs()` block in `frontend/app.js` speak a minimal envelope instead: every message is `{"type": "...", "payload": {...}}` over `ws://.../ws`. `frontend/app.js` implements its own reconnect-with-backoff (Socket.IO did this for free) — if you touch that logic, keep the backoff cap (30s) so a dead backend doesn't get hammered.

7. **Timestamp formats differ by source, and there's one shared fix.** SQLite stores UTC as `YYYY-MM-DD HH:MM:SS` (space, no `Z`); WebSocket events carry `Instant.now().truncatedTo(MILLIS).toString()` (`...T...Z`, via `JsonUtil.nowIso()`). `frontend/app.js` has a single `toUtcDate()` helper for this — route any new timestamp-parsing code through it rather than appending `'Z'` inline (a real bug here previously produced "há NaN min" for live-updated readings).

8. **No ORM.** `Database` (`db/Database.java`) is a thin, synchronous wrapper over plain JDBC (`queryAll`/`queryOne`/`update`, mirroring better-sqlite3's `.all()/.get()/.run()` from before the Java rewrite) — a single shared `Connection`, no pool, no connection-per-request. Keep new queries in that same style rather than introducing a persistence framework.

9. **Gitignored, machine/hardware-specific files must be created locally before running things**: `backend/data/vapid.json` (`java -jar ... generate-vapid`), `firmware/plant-node/main/plant_config.h` (copy from `plant_config.example.h`), and each firmware project's `sdkconfig`/`build/`.

## Backend request contracts

- `IngestController` — device-facing writes: `POST /api/ingest/sensors` (`{device_id, temp_c, humidity_pct, light_pct, plants:[{channel, moisture_pct, pump_active}]}`) and `POST /api/ingest/photo` (multipart; fields `camera_id`, optional `plant_id`, file field `photo`).
- `CommandsController` — device-facing reads: `GET /api/devices/:deviceId/commands`, `GET /api/devices/:deviceId/config`; dashboard-facing: `POST /api/plants/:id/water-now`.
- `PlantsController` — dashboard CRUD for plants plus `/history` and `/events`.
- `PushController` — Web Push VAPID public key + subscribe.

WebSocket events broadcast to the dashboard via `SocketHub.broadcast(type, payload)` (see `IngestController`, `SchedulerService`): `reading:new`, `photo:new`, `event:new`.

## Data model

See `backend/src/main/resources/schema.sql` (SQLite via `sqlite-jdbc`, WAL mode). Tables: `plants` (identity is `device_id`+`channel`), `readings` (time series per plant), `photos` (per camera, optionally linked to a plant), `events` (notification/log history), `commands` (pending device commands, consumed once), `push_subscriptions`, `devices` (last-seen tracking for the offline-alert job in `SchedulerService`, a `ScheduledExecutorService` firing every 5 minutes).
