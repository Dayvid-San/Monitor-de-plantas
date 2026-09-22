# Contexto: Dashboard (`frontend/`)

Carregue este contexto para tarefas no dashboard PWA que roda no navegador do celular/computador.

## Stack

HTML/CSS/JS puro, **sem build step, sem framework, sem bundler**. Servido como estático diretamente pelo backend Java (Javalin, arquivos estáticos externos apontando pra `../frontend`). Editar e recarregar o navegador é suficiente.

## Mapa de arquivos

- `index.html` — shell da página + `<template id="plant-card-template">` usado para clonar cada card de planta via JS (não há componentes, é DOM manual).
- `app.js` — toda a lógica: fetch inicial, render, WebSocket, formulário de nova planta, push. Único arquivo de JS do projeto.
- `style.css` — tema claro/escuro via `prefers-color-scheme` (media query), sem framework CSS.
- `manifest.json` + `sw.js` — instalação como PWA e exibição de notificações push (`self.registration.showNotification` no evento `push` do service worker).
- `icons/icon-192.png`, `icons/icon-512.png` — **placeholders** gerados programaticamente (quadrado verde sólido), não é arte de verdade — substituir antes de qualquer uso "real".

## Bibliotecas externas — vendorizadas, não por CDN

- Chart.js: `<script src="/vendor/chart.umd.js">` — arquivo físico em `frontend/vendor/chart.umd.js`, copiado uma vez do build UMD da lib e commitado no repo (não é buscado de `node_modules` nem de CDN, já que o backend agora é Java). Para atualizar a versão, baixe um novo `chart.umd.js` e substitua o arquivo.

Isso é deliberado: o dashboard precisa funcionar na rede local sem depender de internet.

## Estado e fluxo (`app.js`)

- `state.plants` — `Map<plant_id, { data, card, chart }>`. `data` é o objeto planta vindo da API (inclui `moisture_min`/`moisture_max` usados para colorir o valor de umidade); `card` é o elemento DOM clonado do `<template>`; `chart` é a instância Chart.js daquele card (uma por planta, tipo `line`, eixo Y fixo 0–100).
- `loadPlants()` — busca `GET /api/plants`, monta um card por planta, e dispara `loadHistory()` (busca `GET /api/plants/:id/history?hours=48`) para popular o gráfico antes de qualquer evento em tempo real chegar.
- **WebSocket puro** (`connectWs()`, conecta em `ws(s)://<mesma origem>/ws`) — não é mais Socket.IO (isso mudou quando o backend virou Java; ver `doc_agents/backend.md` item sobre `SocketHub`). Toda mensagem chega como `{"type": "...", "payload": {...}}`: `reading:new` → `updateCardStats()` (atualiza os 4 stats + empurra um ponto no gráfico); `photo:new` → `updateCardPhoto()` (só se `payload.plant_id` estiver setado); `event:new` → hoje só `console.log`, **não há UI para isso ainda** — ponto de extensão natural (ex.: um toast ou uma lista de eventos recentes). `connectWs()` reconecta sozinho com backoff exponencial (1s → dobra a cada queda, até um teto de 30s) — Socket.IO fazia isso de graça; ao mexer nessa lógica, mantenha o teto pra não martelar um backend fora do ar.
- Botões de cada card chamam a API diretamente (`water-now`, `PATCH` para toggle de auto-rega e limiares via `prompt()`, `DELETE`) — não há um framework de formulário, é tudo `addEventListener` direto no card clonado.

## Gotcha de timestamp — `toUtcDate()`

O SQLite grava timestamps UTC como `"YYYY-MM-DD HH:MM:SS"` (espaço, sem `Z`); já os eventos de WebSocket usam `JsonUtil.nowIso()` do lado Java (`...T...Z`). `app.js` tem **uma única função** para lidar com isso:

```js
function toUtcDate(ts) {
  return new Date(ts.endsWith('Z') ? ts : ts + 'Z');
}
```

Qualquer novo código que precise formatar um timestamp vindo da API ou do WebSocket deve passar por `toUtcDate()`. Já existiu um bug real aqui: `relativeTime()` chegou a concatenar `'Z'` sem checar, e para timestamps que já terminavam em `Z` (os de tempo real) isso virava `"...151ZZ"` → `Invalid Date` → `"há NaN min"` na tela.

## Notificações push — limitação conhecida de contexto seguro

O fluxo em `app.js` (registro do service worker + `pushManager.subscribe`) depende da **Push API do navegador**, que só funciona em "secure context": `https://` ou `http://localhost`/`http://127.0.0.1`. Acessando o dashboard por `http://<IP-da-LAN>:3000` (o jeito documentado em `doc/instalacao.md` para abrir do celular), a maioria dos navegadores **recusa registrar o service worker** — o botão "Ativar notificações" vai falhar nesse cenário. Isso ainda não está resolvido no projeto; para funcionar de verdade em rede local é preciso HTTPS (ex.: certificado via `mkcert` distribuído ao celular, ou o recurso de certificado do Tailscale — `tailscale cert`). Ver também `doc_agents/hardware-deployment.md`.

## Ao adicionar um novo tipo de dado ao dashboard

O padrão existente é: 1) adicionar o campo no `<template>` do `index.html`, 2) ler/atualizar esse elemento em `updateCardStats()`/`updateCardPhoto()` em `app.js`, 3) se vier de uma leitura nova, garantir que o backend já está mandando esse campo em `reading:new` (ver `doc_agents/backend.md`) — não existe camada de tipos/schema entre os dois lados, é JSON solto.
