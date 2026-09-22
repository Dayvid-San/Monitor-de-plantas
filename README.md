# Monitor de Plantas

Sistema de monitoramento e rega automática de plantas domésticas: sensores de
umidade/temperatura/luz + câmera analisam as plantas em tempo real, um ESP32
rega automaticamente quando necessário, e um dashboard web (PWA) mostra tudo
e manda notificações para o celular.

## Estrutura

- `firmware/plant-node` — firmware ESP-IDF (C) para o ESP32 que lê os
  sensores e controla as bombas de várias plantas.
- `firmware/plant-cam` — firmware ESP-IDF (C) para o ESP32-CAM que tira fotos
  periódicas das plantas.
- `backend` — servidor Java (Javalin) que recebe os dados, guarda histórico
  (SQLite), analisa as fotos e envia notificações push.
- `frontend` — dashboard web (PWA) instalável no celular.
- `doc` — documentação para humanos: visão geral, lista de compras, guia de
  instalação, uso do dashboard, solução de problemas e FAQ. Comece por
  [`doc/README.md`](doc/README.md).
- `doc_agents` — documentação técnica por contexto, indexada em
  [`AGENTS.md`](AGENTS.md), para agentes de IA trabalhando no código.

## Por onde começar

Se você vai **usar ou montar** o sistema (não mexer no código), vá direto
para [`doc/README.md`](doc/README.md).

Para desenvolvimento:

1. Leia `doc/instalacao.md` para subir o backend e testar tudo com dados
   simulados (`java -jar target/plant-monitor-backend.jar simulate`), sem
   precisar de hardware ainda.
2. Leia `doc/lista-de-compras.md` para a lista de compras e a pinagem antes
   de montar o circuito.
3. Depois de montado, flasheie os ESP32s seguindo o resto de `doc/instalacao.md`.
4. Veja `CLAUDE.md` para comandos de build/run e as decisões de arquitetura
   do projeto.
