[← Voltar ao índice](README.md)

# Instalação

Esta página assume que você já tem os componentes da [lista de compras](lista-de-compras.md)
(ou quer testar antes de comprar qualquer coisa — dá para fazer isso também,
veja a seção final). As etapas 2 e 3 envolvem usar um terminal e gravar
firmware nos ESP32 — se você não tiver familiaridade com isso, vale pedir
ajuda a alguém com experiência em programação para essa parte específica.

## 1. O "cérebro" do sistema (backend)

Isso roda num computador ou Raspberry Pi que fica sempre ligado em casa.
Requer **Java 17+** e **Maven** instalados nesse computador.

```bash
cd backend
mvn package
java -jar target/plant-monitor-backend.jar generate-vapid   # só na primeira vez — habilita as notificações
java -jar target/plant-monitor-backend.jar
```

O painel fica disponível em `http://localhost:3000` nesse computador. Para
abrir do celular, descubra o IP local do computador na sua rede (no Windows,
`ipconfig`; no Linux/Mac, `ip addr` ou `ifconfig`) e acesse, pelo navegador do
celular — **na mesma rede Wi-Fi** —, algo como `http://192.168.1.100:3000`.

## 2. Firmware do ESP32 de sensores

Isso é o programa que roda dentro do ESP32 pequeno preso perto das plantas.
Requer a ferramenta [ESP-IDF](https://docs.espressif.com/projects/esp-idf/en/stable/esp32/get-started/)
instalada no computador usado para gravar (não precisa ser o mesmo que roda o backend).

```bash
cd firmware/plant-node
cp main/plant_config.example.h main/plant_config.h   # depois, ajuste os pinos usados
idf.py set-target esp32
idf.py menuconfig   # configure aqui: Wi-Fi de casa, um nome para o dispositivo, e o IP do backend
idf.py -p /dev/ttyUSB0 flash monitor
```

O nome do dispositivo definido no `menuconfig` (`device_id`) vai precisar
bater exatamente com o que você cadastrar depois no dashboard — veja a etapa 4.

## 3. Firmware da câmera (opcional)

Só necessário se você comprou o módulo ESP32-CAM.

```bash
cd firmware/plant-cam
idf.py set-target esp32
idf.py menuconfig   # configure: Wi-Fi, um nome para a câmera, IP do backend, intervalo entre fotos
idf.py -p /dev/ttyUSB0 flash monitor
```

Lembre-se: durante a gravação, o pino IO0 do ESP32-CAM precisa estar ligado
ao GND (veja [lista de compras](lista-de-compras.md)); solte-o depois, para o
funcionamento normal.

## 4. Cadastrando as plantas no dashboard

1. Abra o dashboard no navegador e clique em **"+ Nova planta"**.
2. Preencha o nome, a espécie (opcional), o **nome do dispositivo** (o mesmo
   `device_id` definido no passo 2) e o **número do canal** dessa planta
   (0 para a primeira planta ligada ao ESP32, 1 para a segunda, etc.).
3. Ajuste os limiares de umidade se quiser — o padrão já funciona bem para a
   maioria das plantas comuns de interior.
4. Repita para cada planta.

A partir daí, os dados aparecem no dashboard assim que o ESP32 mandar a
primeira leitura (por padrão, a cada 60 segundos).

## 5. Ativando as notificações no celular

No topo do dashboard, toque em **"Ativar notificações"** e aceite a
permissão que o navegador pedir. Funciona melhor se você também
"instalar" o dashboard na tela inicial do celular (a maioria dos
navegadores oferece essa opção no menu, algo como "Adicionar à tela
inicial").

> Nota: notificações push exigem uma conexão segura (HTTPS) ou acesso via
> `localhost` — abrindo o dashboard pelo IP da sua rede local, alguns
> navegadores podem recusar ativar essa permissão. Veja
> [Solução de problemas](solucao-de-problemas.md) se isso acontecer.

## 6. Acompanhando as plantas fora de casa

Como tudo roda localmente, para ver o dashboard longe da sua rede Wi-Fi você
precisa de uma forma de alcançar sua casa pela internet. A opção mais
simples e segura é uma VPN pessoal como o [Tailscale](https://tailscale.com/),
instalada tanto no computador que roda o backend quanto no celular — ela cria
uma rede privada entre os seus aparelhos, sem expor nada publicamente na
internet.

---

## Testando sem nenhum hardware

Quer ver o sistema funcionando antes de comprar ou montar qualquer coisa?
Depois de seguir só a etapa 1 (backend), abra um segundo terminal:

```bash
cd backend
java -jar target/plant-monitor-backend.jar simulate
```

Isso cria duas plantas de teste e começa a enviar leituras falsas a cada
poucos segundos — abra o dashboard e veja os números e os gráficos mudando
ao vivo, exatamente como aconteceria com o ESP32 de verdade.

---

Tudo pronto? Veja [Usando o dashboard](usando-o-dashboard.md) para conhecer
cada função da tela.
