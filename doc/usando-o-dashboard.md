[← Voltar ao índice](README.md)

# Usando o dashboard

Depois de [instalado](instalacao.md), o dashboard é a tela que você vai
abrir no dia a dia para acompanhar as plantas. Esta página explica cada
parte dela.

## Barra do topo

- **Indicador "ao vivo" / "desconectado"** — mostra se o navegador está
  recebendo atualizações em tempo real do backend. Se ficar "desconectado"
  por muito tempo, confira se o computador que roda o backend está ligado e
  na rede.
- **"Ativar notificações"** — liga os avisos push no celular (veja a etapa 5
  da [instalação](instalacao.md)).
- **"+ Nova planta"** — abre o formulário de cadastro.

## Cadastrando uma planta

Ao clicar em "+ Nova planta", preencha:

- **Nome** — como você quer identificar a planta (ex. "Samambaia da sala").
- **Espécie** — opcional, só para referência.
- **Device ID** — o nome do ESP32 que cuida dessa planta (definido na
  instalação do firmware).
- **Canal** — o número dessa planta dentro daquele ESP32 (0 para a primeira,
  1 para a segunda, e assim por diante).
- **Umidade mínima/máxima** — os limiares que definem quando a rega
  automática liga e desliga.
- **Rega automática** — deixe marcado para o sistema regar sozinho; desmarque
  se você preferir regar só manualmente por um tempo.

## O card de cada planta

Cada planta cadastrada aparece como um cartão com:

- **Selo de saúde** (canto superior direito) — "Saudável", "Atenção" ou
  "Crítico", baseado na cor da última foto (se você tiver uma câmera
  instalada). Sem câmera, esse selo simplesmente não aparece.
- **Foto mais recente** — a última imagem enviada pela câmera dessa planta.
- **Quatro indicadores**: umidade do solo, temperatura, umidade do ar e luz.
  O número da umidade do solo muda de cor: vermelho quando está abaixo do
  mínimo configurado, azul quando está acima do máximo, e na cor normal
  quando está na faixa ideal.
- **Gráfico** — mostra a variação da umidade do solo nas últimas horas.
- **Data da última leitura** — para você saber se os dados estão realmente
  atualizados.

### Botões do card

- **💧 Regar agora** — pede uma rega manual imediata. O ESP32 verifica por
  esse pedido a cada poucos segundos, então pode levar um pouquinho para a
  bomba realmente ligar.
- **Auto** (interruptor) — liga/desliga a rega automática dessa planta
  especificamente. A mudança chega ao dispositivo em até um ciclo de leitura
  (por padrão, até 60 segundos).
- **Limiares** — permite ajustar os valores mínimo/máximo de umidade que
  disparam a rega automática, sem precisar reprogramar o ESP32.
- **Remover** — apaga a planta e todo o histórico de leituras dela do
  dashboard (as fotos já tiradas continuam guardadas, só perdem o vínculo
  com essa planta).

## Notificações que você pode receber

- A terra de uma planta está seca há muito tempo e a rega automática não
  está dando conta.
- A bomba ligou várias vezes seguidas, mas a umidade não está subindo —
  sinal de que o reservatório pode estar vazio ou a mangueira entupida.
- Uma foto recente indica que a planta pode estar com problema (folhas
  amareladas/secas).
- Um dos ESP32 parou de mandar dados há mais de 30 minutos.

---

Se algo não estiver se comportando como o esperado, veja
[Solução de problemas](solucao-de-problemas.md).
