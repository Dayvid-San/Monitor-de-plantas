# Contexto: Firmware da câmera (`firmware/plant-cam`)

Carregue este contexto para tarefas no firmware ESP-IDF (C puro) do ESP32-CAM que fotografa as plantas periodicamente.

## Stack

ESP-IDF v5.x, C puro. Componentes: `esp_wifi`/`esp_netif`/`esp_event`/`nvs_flash`/`esp_http_client` (mesmos do plant-node) + `esp32-camera` (componente externo `espressif/esp32-camera`, baixado pelo Component Manager via `main/idf_component.yml` — o primeiro build precisa de internet).

## Mapa de arquivos

- `main.c` — `camera_init()` monta o `camera_config_t` (pinagem, formato JPEG, tamanho de frame, onde fica o framebuffer) e chama `esp_camera_init()`; loop principal captura com `esp_camera_fb_get()`, envia com `upload_photo()`, libera com `esp_camera_fb_return()`, dorme `CONFIG_PLANT_CAM_CAPTURE_INTERVAL_SEC`.
- `camera_pins.h` — pinagem fixa do módulo **AI-Thinker** OV2640. Trocar de módulo de câmera exige trocar este arquivo inteiro.
- `wifi_connect.c/.h` — **cópia independente** do mesmo arquivo usado em `firmware/plant-node` (são dois binários/projetos ESP-IDF separados, não há código compartilhado entre eles). Se corrigir um bug de conexão Wi-Fi aqui, replicar manualmente no outro projeto (ou vice-versa).
- `upload_client.c/.h` — monta e envia o multipart/form-data manualmente (ver seção própria).
- `Kconfig.projbuild` — menu "Plant Cam Configuration": Wi-Fi, `PLANT_CAM_ID`, `PLANT_CAM_PLANT_ID` (opcional, -1 = sem vínculo), host/porta do backend, intervalo de captura.
- `idf_component.yml` — declara a dependência do componente `esp32-camera`.
- `sdkconfig.defaults` — habilita PSRAM (`CONFIG_SPIRAM=y` e modo quad) — **obrigatório**, o framebuffer da câmera é alocado em PSRAM (`fb_location = CAMERA_FB_IN_PSRAM` em `main.c`).

## Upload multipart (`upload_client.c`)

Não usa nenhuma lib de multipart — monta o boundary (`----plantcamboundary7d81b3`) e o cabeçalho/rodapé como strings pequenas, e escreve em **três chamadas separadas** de `esp_http_client_write()` (cabeçalho de texto → bytes crus do JPEG → rodapé), via `esp_http_client_open(client, content_length)` com o `content_length` total pré-calculado. Isso é deliberado: evita concatenar o JPEG inteiro num segundo buffer além do framebuffer já alocado em PSRAM. Se for preciso adicionar mais campos ao formulário, adicione-os ao `header` (antes do campo `photo`), nunca depois — o campo de arquivo precisa ser o último antes do rodapé.

`plant_id < 0` é o sentinel para "sem vínculo": o campo é omitido do corpo multipart inteiramente (não envia `plant_id=-1`).

## Configuração de captura (`main.c`)

`FRAMESIZE_SVGA` (800x600), `jpeg_quality = 12` (escala 0–63, menor = melhor), `fb_count = 1`, `CAMERA_GRAB_LATEST`. É aqui que se ajusta resolução/qualidade se o upload estiver lento ou os arquivos grandes demais para a rede/backend.

## Contrato com o backend

`POST /api/ingest/photo`, multipart, campos `camera_id` (sempre) e `plant_id` (se configurado), arquivo no campo `photo`. Ver `doc_agents/backend.md` para o que o servidor faz com isso (heurística de saúde por cor, eventos, push).

## Pegadinhas de hardware ao mexer aqui

- O ESP32-CAM **não tem USB nativo** — grava só com adaptador FTDI USB-serial.
- Durante o flash, o pino **IO0 precisa estar ligado ao GND**; solte antes de resetar/rodar normalmente.
- A porta 5V de um FTDI comum costuma não entregar corrente suficiente durante o flash — recomenda-se fonte 5V externa separada (ver `doc/lista-de-compras.md`).
- Sem PSRAM habilitada (`sdkconfig.defaults`), `esp_camera_init()` falha ou trava em resoluções acima de QVGA.
