const state = { plants: new Map() }; // id -> { data, chart, cardEl }

const grid = document.getElementById('plants-grid');
const emptyState = document.getElementById('empty-state');
const template = document.getElementById('plant-card-template');
const statusEl = document.getElementById('connection-status');

function healthLabelPt(label) {
  return { saudavel: 'Saudável', atencao: 'Atenção', critico: 'Crítico' }[label] || 'Sem análise';
}

function moistureClass(pct, min, max) {
  if (pct === null || pct === undefined) return '';
  if (pct < min) return 'stat__moisture--low';
  if (pct > max) return 'stat__moisture--high';
  return 'stat__moisture--ok';
}

function toUtcDate(ts) {
  return new Date(ts.endsWith('Z') ? ts : ts + 'Z');
}

function relativeTime(isoTs) {
  if (!isoTs) return 'sem leituras ainda';
  const diffMs = Date.now() - toUtcDate(isoTs).getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return 'agora mesmo';
  if (mins < 60) return `há ${mins} min`;
  const hours = Math.round(mins / 60);
  return `há ${hours}h`;
}

async function api(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(`Erro ${res.status} em ${path}`);
  if (res.status === 204) return null;
  return res.json();
}

function createChart(canvas) {
  return new Chart(canvas.getContext('2d'), {
    type: 'line',
    data: {
      labels: [],
      datasets: [
        {
          label: 'Umidade (%)',
          data: [],
          borderColor: '#2e7d32',
          backgroundColor: 'rgba(46,125,50,0.15)',
          tension: 0.3,
          fill: true,
          pointRadius: 0,
        },
      ],
    },
    options: {
      animation: false,
      scales: {
        y: { min: 0, max: 100, ticks: { stepSize: 25 } },
        x: { display: false },
      },
      plugins: { legend: { display: false } },
    },
  });
}

function buildCard(plant) {
  const node = template.content.cloneNode(true);
  const card = node.querySelector('.plant-card');
  card.dataset.plantId = plant.id;

  card.querySelector('.btn-water').addEventListener('click', async () => {
    await api(`/plants/${plant.id}/water-now`, { method: 'POST' });
  });

  card.querySelector('.toggle-auto-water').addEventListener('change', async (e) => {
    await api(`/plants/${plant.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ auto_water: e.target.checked }),
    });
  });

  card.querySelector('.btn-edit').addEventListener('click', async () => {
    const entry = state.plants.get(plant.id);
    const current = entry.data;
    const min = prompt('Umidade mínima (%) para acionar rega:', current.moisture_min);
    if (min === null) return;
    const max = prompt('Umidade máxima (%) para parar a rega:', current.moisture_max);
    if (max === null) return;
    await api(`/plants/${plant.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ moisture_min: Number(min), moisture_max: Number(max) }),
    });
    entry.data.moisture_min = Number(min);
    entry.data.moisture_max = Number(max);
  });

  card.querySelector('.btn-delete').addEventListener('click', async () => {
    if (!confirm(`Remover "${plant.name}"? O histórico será apagado.`)) return;
    await api(`/plants/${plant.id}`, { method: 'DELETE' });
    card.remove();
    state.plants.delete(plant.id);
    updateEmptyState();
  });

  grid.appendChild(card);
  const chart = createChart(card.querySelector('.plant-card__chart'));
  return { card, chart };
}

function updateCardStats(plantId, reading) {
  const entry = state.plants.get(plantId);
  if (!entry) return;
  const { card, data } = entry;

  const moistureEl = card.querySelector('.stat__moisture');
  moistureEl.textContent = reading.moisture_pct != null ? `${Math.round(reading.moisture_pct)}%` : '--%';
  moistureEl.className = `stat__value stat__moisture ${moistureClass(reading.moisture_pct, data.moisture_min, data.moisture_max)}`;

  card.querySelector('.stat__temp').textContent = reading.temp_c != null ? `${reading.temp_c.toFixed(1)}°C` : '--°C';
  card.querySelector('.stat__humidity').textContent = reading.humidity_pct != null ? `${Math.round(reading.humidity_pct)}%` : '--%';
  card.querySelector('.stat__light').textContent = reading.light_pct != null ? `${Math.round(reading.light_pct)}%` : '--%';
  card.querySelector('.plant-card__updated').textContent = `Última leitura: ${relativeTime(reading.ts)}`;

  const chart = entry.chart;
  chart.data.labels.push(toUtcDate(reading.ts).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }));
  chart.data.datasets[0].data.push(reading.moisture_pct);
  if (chart.data.labels.length > 100) {
    chart.data.labels.shift();
    chart.data.datasets[0].data.shift();
  }
  chart.update('none');
}

function updateCardPhoto(plantId, photo) {
  const entry = state.plants.get(plantId);
  if (!entry) return;
  const img = entry.card.querySelector('.plant-card__photo img');
  const noPhoto = entry.card.querySelector('.no-photo');
  img.src = photo.url;
  img.hidden = false;
  noPhoto.hidden = true;

  const badge = entry.card.querySelector('.health-badge');
  badge.textContent = healthLabelPt(photo.health_label);
  badge.className = `health-badge health-badge--${photo.health_label}`;
}

function updateEmptyState() {
  emptyState.hidden = state.plants.size > 0;
}

async function loadHistory(plantId, chart) {
  const readings = await api(`/plants/${plantId}/history?hours=48`);
  chart.data.labels = readings.map((r) =>
    toUtcDate(r.ts).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  );
  chart.data.datasets[0].data = readings.map((r) => r.moisture_pct);
  chart.update('none');
}

async function loadPlants() {
  const plants = await api('/plants');
  for (const plant of plants) {
    const { card, chart } = buildCard(plant);
    state.plants.set(plant.id, { data: plant, card, chart });

    card.querySelector('.plant-card__name').textContent = plant.name;
    card.querySelector('.plant-card__species').textContent = plant.species || '';
    card.querySelector('.toggle-auto-water').checked = !!plant.auto_water;

    if (plant.latest_reading) updateCardStats(plant.id, plant.latest_reading);
    if (plant.latest_photo) updateCardPhoto(plant.id, { url: `/photos/${plant.latest_photo.filepath}`, health_label: plant.latest_photo.health_label });

    loadHistory(plant.id, chart).catch(() => {});
  }
  updateEmptyState();
}

// --- Formulário de nova planta ---
const newPlantPanel = document.getElementById('new-plant-panel');
document.getElementById('btn-new-plant').addEventListener('click', () => {
  newPlantPanel.hidden = !newPlantPanel.hidden;
});
document.getElementById('btn-cancel-new-plant').addEventListener('click', () => {
  newPlantPanel.hidden = true;
});
document.getElementById('new-plant-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const payload = {
    name: form.name.value,
    species: form.species.value || null,
    device_id: form.device_id.value,
    channel: Number(form.channel.value),
    moisture_min: Number(form.moisture_min.value),
    moisture_max: Number(form.moisture_max.value),
    auto_water: form.auto_water.checked,
  };
  const plant = await api('/plants', { method: 'POST', body: JSON.stringify(payload) });
  const { card, chart } = buildCard(plant);
  state.plants.set(plant.id, { data: plant, card, chart });
  card.querySelector('.plant-card__name').textContent = plant.name;
  card.querySelector('.plant-card__species').textContent = plant.species || '';
  card.querySelector('.toggle-auto-water').checked = !!plant.auto_water;
  updateEmptyState();
  form.reset();
  newPlantPanel.hidden = true;
});

// --- WebSocket (tempo real) ---
// WebSocket puro (não Socket.IO) — o backend Java fala esse protocolo direto,
// sem uma lib de compatibilidade. Mensagens chegam como {type, payload}.
let ws;
let reconnectDelay = 1000;

function connectWs() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  ws = new WebSocket(`${protocol}//${location.host}/ws`);

  ws.addEventListener('open', () => {
    statusEl.textContent = 'ao vivo';
    statusEl.className = 'status status--online';
    reconnectDelay = 1000;
  });

  ws.addEventListener('close', () => {
    statusEl.textContent = 'desconectado';
    statusEl.className = 'status status--offline';
    setTimeout(connectWs, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 30000);
  });

  ws.addEventListener('error', () => ws.close());

  ws.addEventListener('message', (event) => {
    const { type, payload } = JSON.parse(event.data);
    if (type === 'reading:new') {
      updateCardStats(payload.plant_id, payload);
    } else if (type === 'photo:new') {
      if (payload.plant_id) updateCardPhoto(payload.plant_id, payload);
    } else if (type === 'event:new') {
      console.log('[evento]', payload.type, payload.message);
    }
  });
}

connectWs();

// --- Notificações push ---
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)));
}

document.getElementById('btn-notifications').addEventListener('click', async () => {
  try {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      alert('Este navegador não suporta notificações push.');
      return;
    }
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    const registration = await navigator.serviceWorker.register('/sw.js');
    const { publicKey } = await api('/push/public-key');
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
    await fetch('/api/push/subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(subscription.toJSON()),
    });
    alert('Notificações ativadas!');
  } catch (err) {
    console.error(err);
    alert('Não foi possível ativar notificações: ' + err.message);
  }
});

loadPlants().catch((err) => console.error('Erro ao carregar plantas:', err));
