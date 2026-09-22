[← Voltar ao índice](README.md)

# Lista de compras e pinagem

## Para o nó de sensores + rega (um kit atende até ~6 plantas)

| Item | Quantidade | Observação |
|---|---|---|
| ESP32 DevKit (30/38 pinos) | 1 | Qualquer devkit genérico com Wi-Fi serve |
| Sensor de umidade do solo capacitivo | 1 por planta | Prefira o capacitivo — o resistivo (mais barato) corrói rápido em contato com a terra molhada |
| Sensor DHT22 (AM2302) | 1 por kit | Mede temperatura e umidade do ar, compartilhado entre todas as plantas do kit |
| LDR + resistor 10kΩ | 1 por kit | Mede luminosidade, compartilhado |
| Módulo relé (1 canal por planta — ex. um relé de 4 canais para 4 plantas) | 1 por planta | Confira se é ativo em nível alto ou baixo (isso é ajustado no firmware) |
| Mini bomba de água submersível 5V/6V | 1 por planta | + um reservatório de água para cada uma |
| Mangueira de silicone 4–6mm | 1 por planta | Da bomba até a terra do vaso |
| Fonte 5V (2A ou mais) | 1 | Alimenta o ESP32, os relés e as bombas |
| Protoboard + jumpers | — | Para a montagem |
| Diodo de proteção (1N4007) | 1 por bomba | Só se o módulo relé não tiver essa proteção embutida |

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
| Fonte 5V separada (recomendado) | 1 | A porta 5V do adaptador FTDI geralmente não entrega energia suficiente durante a gravação |

## Dicas antes de montar

- Ligue as bombas numa fonte de energia com corrente de sobra — elas puxam
  bastante energia no momento em que ligam.
- Nunca deixe o sensor de umidade submerso além da marca indicada pelo
  fabricante — só a ponta deve ficar em contato com a terra.
- Teste cada bomba separadamente (num copo d'água, fora da terra) antes de
  instalar de vez, para confirmar que ela liga e desliga do jeito esperado.
- O ESP32-CAM precisa que o pino **IO0** seja ligado ao terra (GND) só
  durante a gravação do programa — depois disso ele deve ficar solto para o
  funcionamento normal.

---

Com os componentes em mãos, siga para a [instalação](instalacao.md).
