# Contexto: Firmware do nó de sensores (`firmware/plant-node`)

Carregue este contexto para tarefas no firmware ESP-IDF (C puro) que lê sensores e controla as bombas de rega.

## Stack

ESP-IDF v5.x, C puro (sem Arduino/C++). Componentes usados: `esp_wifi`, `esp_netif`, `esp_event`, `nvs_flash`, `esp_http_client`, `json` (cJSON), `driver` (GPIO), `esp_adc` (ADC oneshot).

## Mapa de arquivos

- `main.c` — `app_main()`: conecta Wi-Fi, inicializa ADC e bombas, entra num loop com dois timers independentes controlados por `esp_timer_get_time()`.
- `wifi_connect.c/.h` — conexão Wi-Fi station padrão (event group, retry até `MAX_RETRY`). Genérico, recebe SSID/senha por parâmetro.
- `soil_sensor.c/.h` — wrapper sobre `adc_oneshot`: um único `adc_oneshot_unit_handle_t` (ADC_UNIT_1) compartilhado, canais configurados sob demanda e cacheados (`s_channel_configured[]`). Converte leitura bruta em % usando `SOIL_RAW_DRY`/`SOIL_RAW_WET` (definidos em `plant_config.h`).
- `dht22.c/.h` — driver bit-bang do DHT22 (sem componente externo). Usa `esp_timer_get_time()` para medir largura de pulso com timeout, e `portDISABLE_INTERRUPTS()`/`portENABLE_INTERRUPTS()` durante a janela crítica de leitura dos 40 bits. Retorna `ESP_ERR_TIMEOUT` ou `ESP_ERR_INVALID_CRC` em falha — o chamador trata isso como leitura ausente (`NAN`), não aborta o ciclo.
- `pump.c/.h` — configura os GPIOs de relé como saída e liga/desliga respeitando `PUMP_ACTIVE_LEVEL`.
- `backend_client.c/.h` — monta/envia JSON (cJSON) para `POST /api/ingest/sensors`, e faz `GET` + parse de `/commands` e `/config`. Usa um buffer estático (`s_response_buf`, 1KB) preenchido via `HTTP_EVENT_ON_DATA` no event handler — não é reentrante/thread-safe, assume uso de uma única task.
- `plant_config.example.h` — **copiar para `plant_config.h`** (gitignored) antes de compilar. Define `PLANT_CHANNELS[]` (um item por planta: canal ADC do sensor, GPIO do relé, limiares padrão, tempo máximo de bomba), `PUMP_ACTIVE_LEVEL`, pinos/canais compartilhados (`DHT22_GPIO`, `LDR_ADC_CHANNEL`), calibração (`SOIL_RAW_DRY`/`WET`) e `PUMP_COOLDOWN_SEC`.
- `Kconfig.projbuild` — menu "Plant Node Configuration": SSID/senha Wi-Fi, `PLANT_DEVICE_ID`, host/porta do backend, intervalos de leitura e de poll de comandos.

## Loop principal (`main.c`)

Um único `while(1)` com tick de 1s, dois timers independentes (segundos, via `esp_timer_get_time()/1000000`):

- A cada `CONFIG_PLANT_COMMAND_POLL_INTERVAL_SEC` (padrão 8s): `handle_pending_commands()` — busca comandos `water_now` e chama `run_pump_cycle()` para o canal correspondente, ignorando o cooldown (é um pedido explícito do usuário).
- A cada `CONFIG_PLANT_READ_INTERVAL_SEC` (padrão 60s): `refresh_effective_config()` (busca `moisture_min/max/auto_water` do backend, mantém os valores atuais se a chamada falhar) seguido de `read_and_report()` (lê DHT22 + luz + cada canal de solo, decide rega automática, envia tudo).

### Estado em runtime (arrays indexados pela posição em `PLANT_CHANNELS[]`, não pelo número do canal)

- `s_effective[]` — limiares/`auto_water` efetivos, inicializados a partir de `plant_config.h` e sobrescritos pelo `refresh_effective_config()` quando o backend responde. **É assim que o toggle "Auto" e a edição de limiares do dashboard chegam ao dispositivo** — com atraso de até um ciclo de leitura.
- `s_last_water_us[]` — timestamp da última rega, usado para o cooldown (`PUMP_COOLDOWN_SEC`) na rega automática (não se aplica à rega manual).
- `s_watered_since_last_report[]` — flag para reportar `pump_active=true` na leitura enviada mesmo que a bomba já tenha sido desligada antes do envio (o ciclo de rega roda e termina dentro do mesmo ciclo síncrono, antes do POST).

### `run_pump_cycle()`

Liga o relé, e a cada 1s (até `pump_max_seconds`, trava de segurança) relê a umidade do canal e para cedo se atingir `moisture_max` — é o único lugar que efetivamente aciona uma bomba.

## Contrato com o backend

Ver `doc_agents/backend.md` para o lado servidor. Resumo do que este firmware fala:
- `POST /api/ingest/sensors` a cada ciclo de leitura.
- `GET /api/devices/:device_id/commands` a cada poll de comando.
- `GET /api/devices/:device_id/config` a cada ciclo de leitura, antes de decidir a rega automática.

## Pegadinhas ao mexer aqui

- **`plant_config.h` não existe no repo** (só o `.example.h`) — sem copiar e ajustar, `idf.py build` falha por header ausente.
- Sensores de umidade e o LDR precisam estar em pinos **ADC1** (`GPIO32`–`GPIO39`) — ADC2 conflita com o driver Wi-Fi e não pode ser lido com Wi-Fi ativo.
- `PUMP_ACTIVE_LEVEL` depende do módulo relé físico (muitos módulos baratos são ativos em nível baixo) — inverter isso sem checar o hardware liga a bomba permanentemente em vez de desligada no boot.
- `PLANT_CHANNEL_COUNT` vem de `sizeof(PLANT_CHANNELS)/sizeof(PLANT_CHANNELS[0])` — adicionar uma planta é só adicionar uma entrada ao array, nada mais precisa mudar no código.
- O firmware **não valida** se o `channel` que ele envia tem uma planta cadastrada no backend — leituras de canais não cadastrados são aceitas pelo firmware e só descartadas do lado do backend (ver `doc_agents/backend.md`).
- `backend_client.c` não é thread-safe (buffer estático global) — se algum dia este firmware ganhar mais de uma task chamando funções de rede, isso precisa ser revisto.
