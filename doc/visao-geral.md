[← Voltar ao índice](README.md)

# Visão geral

O Monitor de Plantas cuida das suas plantas quando você não tem tempo: mede a
umidade da terra, a luz e o clima ao redor, rega automaticamente quando
necessário, tira fotos periódicas e te avisa no celular se algo estiver
errado — tudo isso sem depender da nuvem, rodando na sua própria rede de casa.

## As três partes do sistema

```
┌────────────────────┐        ┌──────────────────────┐        ┌───────────────────┐
│  ESP32 de sensores  │──────▶│   Computador/         │──────▶│  Dashboard no      │
│  + bombas de água   │  Wi-Fi │   Raspberry Pi        │  Wi-Fi │  navegador do      │
│  (por planta)       │◀──────│   (roda o "cérebro")  │◀──────│  celular            │
└────────────────────┘        └──────────────────────┘        └───────────────────┘
        ▲
        │ Wi-Fi
┌────────────────────┐
│  ESP32-CAM          │
│  (fotos periódicas) │
└────────────────────┘
```

- **ESP32 de sensores** — um pequeno computador de baixo custo, preso perto
  das plantas, com um sensor de umidade por vaso, um sensor de temperatura/
  umidade do ar, um sensor de luz, e uma bomba de água por planta. Ele decide
  sozinho quando regar — não depende do computador estar ligado para isso.
- **ESP32-CAM** (opcional) — uma câmera pequena que tira uma foto das plantas
  de tempos em tempos, para avaliar a saúde delas pela cor das folhas.
- **O "cérebro"** — um programa que roda o tempo todo num computador ou
  Raspberry Pi já existente em casa. Ele recebe os dados dos sensores, guarda
  o histórico, avalia as fotos, e manda os avisos para o celular.
- **O dashboard** — a tela que você acessa pelo navegador do celular (ou
  computador), como um site normal, mostrando tudo em tempo real.

## O que o sistema faz

- Mostra em tempo real a umidade da terra, temperatura, umidade do ar e luz
  de cada planta.
- Rega automaticamente quando a terra fica seca demais, respeitando um tempo
  mínimo entre regas para não encharcar o vaso.
- Deixa você regar manualmente com um toque, direto do celular.
- Tira fotos periódicas e avalia se a planta parece saudável, com atenção ou
  crítica, pela cor das folhas.
- Manda notificação no celular quando: a terra está seca há muito tempo, a
  bomba ligou mas não resolveu (reservatório vazio, mangueira entupida), uma
  foto indica problema, ou um sensor parou de responder.

## O que o sistema **não** faz (hoje)

- Não identifica doenças específicas nem espécies de plantas — a avaliação
  da foto é uma estimativa simples pela cor (mais verde = melhor), não um
  diagnóstico.
- Não funciona fora de casa "prontinho" — para acessar de fora da sua rede
  Wi-Fi, é preciso configurar acesso remoto à parte (veja [Instalação](instalacao.md)).
- Não tem loja de aplicativo — o "app" é o próprio navegador do celular,
  que pode ser "instalado" na tela inicial.

Pronto para montar? Veja a [lista de compras](lista-de-compras.md) e depois a
[instalação](instalacao.md).
