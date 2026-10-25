[← Voltar ao índice](README.md)

# Lista de compras e pinagem

## Para o nó de sensores + rega (um kit atende até 5 plantas)

| Item | Quantidade | Observação |
|---|---|---|
| ESP32 DevKit (30/38 pinos) | 1 | Tipo "ESP32 DevKit V1" / ESP32-WROOM-32 com os pinos GPIO36, 39, 34, 25, 26 e 4 expostos. **Não** serve a placa "ESP32 UNO / Wemos R32" (só 1 entrada analógica) |
| Sensor de umidade do solo capacitivo | 1 por planta | Prefira o capacitivo — o resistivo (mais barato) corrói rápido em contato com a terra molhada |
| Sensor DHT22 (AM2302) | 1 por kit | Mede temperatura e umidade do ar, compartilhado entre todas as plantas do kit. O resistor de 10kΩ só é necessário no DHT22 solto de 4 pinos; o módulo de 3 pinos já o traz |
| LDR + resistor 10kΩ | 1 por kit | Mede luminosidade, compartilhado |
| Módulo relé (1 canal por planta — ex. um relé de 4 canais para 4 plantas) | 1 por planta | Prefira um com optoacoplador e "compatível com 3,3V" (alguns de 5V não disparam com o sinal do ESP32). Confira se é ativo em nível alto ou baixo (ajustado no firmware, `PUMP_ACTIVE_LEVEL`) |
| Mini bomba de água submersível 5V/6V | 1 por planta | + um reservatório de água para cada uma |
| Mangueira de silicone 4–6mm | 1 por planta | Da bomba até a terra do vaso |
| Fonte 5V (2A ou mais) | 1 | Alimenta o ESP32, os relés e as bombas |
| Protoboard + jumpers | — | Para a montagem |
| Diodo de proteção (1N4007) | 1 por bomba | Ligado nos terminais da bomba (faixa para o lado do +). O diodo que vem no módulo relé protege só a bobina do relé, não a bomba |
| Cabo USB de dados (micro-USB ou USB-C, conforme a placa) | 1 | Para gravar o ESP32 — cabos só de carga não funcionam |
| Jumpers macho-fêmea e fêmea-fêmea | — | Para os conectores de 3 pinos dos sensores |
| Esmalte ou verniz transparente | 1 | Para vedar a borda exposta do sensor capacitivo, que absorve água e se degrada |

### Onde ligar cada sensor (pinagem padrão)

- Sensores de umidade: um pino por planta (ex. GPIO36 para a primeira planta, GPIO39 para a segunda, e assim por diante)
- Relés das bombas: um pino digital por planta (ex. GPIO25, GPIO26, ...)
- DHT22: um único pino (ex. GPIO4), com resistor de 10kΩ entre o fio de dados e o positivo
- LDR: um único pino (ex. GPIO34), com um resistor de 10kΩ formando um divisor de tensão

*(A pinagem exata usada na sua instalação fica registrada no arquivo de
configuração do firmware — quem for mexer no código vê os detalhes em
`firmware/plant-node/main/plant_config.example.h`.)*

## Para a câmera (opcional — uma por área que você quiser fotografar)

| Item | Quantidade | Observação |
|---|---|---|
| ESP32-CAM (AI-Thinker, com câmera OV2640) | 1 | Já vem com a câmera embutida |
| Programador FTDI USB-serial (5V/3.3V) | 1 | O ESP32-CAM não tem entrada USB — esse adaptador é usado só para gravar o programa nele |
| Fonte 5V separada (recomendado, 1A ou mais) | 1 | A porta 5V do adaptador FTDI geralmente não entrega energia suficiente durante a gravação, e a câmera reinicia ou perde Wi-Fi com energia fraca |
| Base ESP32-CAM-MB (alternativa ao FTDI) | 1 | Encaixa por baixo da placa e já tem a entrada micro-USB; dispensa o FTDI e o jumper do IO0. Anúncios que dizem "não incluso MB" não trazem essa base |

Também precisa de um computador ligado o tempo todo na sua rede para o backend (Java 17+ e Maven; um PC antigo ou Raspberry Pi serve).

## Dicas antes de montar

- Ligue as bombas numa fonte de energia com corrente de sobra — elas puxam
  bastante energia no momento em que ligam.
- Nunca deixe o sensor de umidade submerso além da marca indicada pelo
  fabricante — só a ponta deve ficar em contato com a terra.
- Ligue o GND do ESP32, do módulo relé e da fonte juntos (GND comum).
- Mantenha a ponta da mangueira acima do nível da água do reservatório; abaixo dele, a água continua escoando por sifão depois que a bomba desliga.
- Confira o diâmetro da saída da bomba antes de comprar a mangueira.
- Meça cada sensor de umidade seco ao ar e na água e ajuste `SOIL_RAW_DRY`/`SOIL_RAW_WET` em `plant_config.h` — os valores de exemplo não servem para todos os sensores.
- Teste cada bomba separadamente (num copo d'água, fora da terra) antes de
  instalar de vez, para confirmar que ela liga e desliga do jeito esperado.
- O ESP32-CAM precisa que o pino **IO0** seja ligado ao terra (GND) só
  durante a gravação do programa — depois disso ele deve ficar solto para o
  funcionamento normal.

---

Com os componentes em mãos, siga para a [instalação](instalacao.md).
