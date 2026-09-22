# AGENTS.md

Guia de entrada para agentes de IA trabalhando neste repositório: um sistema de monitoramento e rega automática de plantas domésticas, com firmware ESP32 (C/ESP-IDF), backend Java (Javalin) e um dashboard PWA.

Para comandos de build/run rápidos e as decisões de arquitetura que atravessam o projeto inteiro, veja `CLAUDE.md` na raiz primeiro.

Este arquivo aponta para o contexto certo por área — carregue **apenas o(s) arquivo(s) relevante(s)** para a tarefa em mãos, não todos:

- [`doc_agents/backend.md`](doc_agents/backend.md) — API HTTP, SQLite, análise de fotos, notificações push, agendador. Tarefas em `backend/`.
- [`doc_agents/firmware-plant-node.md`](doc_agents/firmware-plant-node.md) — firmware ESP-IDF (C) do ESP32 que lê sensores e controla as bombas. Tarefas em `firmware/plant-node/`.
- [`doc_agents/firmware-plant-cam.md`](doc_agents/firmware-plant-cam.md) — firmware ESP-IDF (C) do ESP32-CAM que fotografa as plantas. Tarefas em `firmware/plant-cam/`.
- [`doc_agents/frontend.md`](doc_agents/frontend.md) — dashboard PWA (HTML/CSS/JS puro, sem build). Tarefas em `frontend/`.
- [`doc_agents/hardware-deployment.md`](doc_agents/hardware-deployment.md) — pinagem, calibração de sensores, particularidades de gravação do ESP32-CAM, topologia de rede e limitações de acesso remoto/HTTPS. Para tarefas que não são só código: dúvidas de montagem, wiring, ou como o sistema roda fisicamente em casa.

## Documentação para humanos (não escrita para agentes, mas útil como referência)

- `README.md` — visão geral técnica e por onde começar.
- [`doc/README.md`](doc/README.md) — documentação completa para quem usa/monta
  o sistema (índice navegável): visão geral, lista de compras, instalação,
  uso do dashboard, solução de problemas, FAQ.
