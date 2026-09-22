# Contexto: Hardware e implantação

Carregue este contexto para tarefas sobre pinagem, calibração de sensores, lista de compras, ou como o sistema roda fisicamente em casa (rede, acesso remoto). Para configuração de software/build dos firmwares, ver `doc_agents/firmware-plant-node.md` e `doc_agents/firmware-plant-cam.md`.

## Documentos de referência já existentes (voltados ao usuário final)

- `doc/lista-de-compras.md` — lista de compras completa e pinagem sugerida.
- `doc/instalacao.md` — passo a passo de instalação (backend, firmware, cadastro de plantas, acesso remoto).

Este arquivo complementa esses dois com o que um agente precisa saber para *tomar decisões* de código relacionadas a hardware, não repete a lista de compras.

## Pinagem padrão (nó de sensores, `firmware/plant-node/main/plant_config.example.h`)

- Sensores de umidade capacitivos: pinos **ADC1** apenas (ex. GPIO36/GPIO39 nos dois primeiros canais do exemplo) — ADC2 não pode ser lido com Wi-Fi ativo no ESP32.
- Relés das bombas: GPIOs digitais quaisquer (exemplo usa GPIO25/GPIO26).
- DHT22 (temperatura/umidade do ar, compartilhado entre as plantas do nó): GPIO4, com pull-up de 10kΩ entre dados e VCC.
- LDR (luminosidade, compartilhado): GPIO34, com divisor resistivo de 10kΩ.

Esses valores são só o exemplo em `plant_config.example.h` — o arquivo real (`plant_config.h`, gitignored) pode divergir por instalação. Ao investigar um bug de leitura, sempre confirme a pinagem no `plant_config.h` local, não assuma o exemplo.

## Calibração do sensor de umidade

`SOIL_RAW_DRY`/`SOIL_RAW_WET` em `plant_config.h` são leituras brutas de ADC (0–4095) medidas manualmente: sensor seco ao ar vs. sensor em água. Sem recalibrar por sensor físico, a porcentagem reportada pode ficar sistematicamente errada — isso é esperado ser feito na instalação, não é um bug de firmware.

## ESP32-CAM (AI-Thinker) — particularidades de gravação

- Não tem USB nativo: precisa de adaptador FTDI USB-serial (3.3V/5V).
- Pino **IO0 deve ir ao GND só durante o flash**; solto no funcionamento normal.
- A porta 5V de um FTDI comum geralmente não aguenta a corrente de pico da câmera durante o flash — recomenda-se fonte 5V externa dedicada.
- Precisa de PSRAM habilitada (`sdkconfig.defaults` do `plant-cam`) — sem isso `esp_camera_init()` falha em resoluções acima de QVGA.

## Topologia de implantação

```
[ESP32 nó de sensores] --Wi-Fi (LAN doméstica)--> [PC/Raspberry Pi rodando backend:3000] <--Wi-Fi-- [ESP32-CAM]
                                                          ^
                                                          | HTTP (LAN)
                                                    [celular, navegador]
```

O backend roda sempre ligado num computador/Raspberry Pi já existente em casa — não há nuvem, container ou reverse proxy configurado por padrão. O dashboard é acessado por `http://<IP-da-LAN>:3000`.

## Limitação conhecida: acesso remoto e HTTPS

`doc/instalacao.md` recomenda o Tailscale para acessar o dashboard fora de casa (VPN pessoal, sem expor portas na internet). Isso resolve *alcançar* o backend remotamente, mas **não resolve sozinho as notificações push**: a Push API do navegador exige um "secure context" (HTTPS, ou `localhost`) para o service worker se registrar (ver `doc_agents/frontend.md`). Acessar via IP do Tailscale ainda é HTTP puro por padrão. Para notificações push funcionarem de fato fora da rede local, falta configurar HTTPS na frente do backend — por exemplo com `tailscale cert` (o Tailscale pode emitir certificado para o nome MagicDNS do dispositivo) e servir o backend atrás disso, ou outro proxy TLS equivalente. Isso **não está implementado** no projeto hoje; é um bom próximo passo antes de prometer notificações remotas funcionando de ponta a ponta.

## Escalando para mais plantas

Um nó ESP32 aguenta confortavelmente até ~6 canais (limitado por pinos ADC1 livres e GPIOs para relé). Para mais plantas, a orientação do projeto é somar um **segundo nó ESP32** com outro `device_id` (não expandir um nó além da contagem de pinos disponíveis) — o backend já é multi-dispositivo por design (`plants.device_id`), não precisa de mudança de código para isso.
