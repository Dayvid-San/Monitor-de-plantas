[← Voltar ao índice](README.md)

# Solução de problemas

## Uma planta cadastrada não mostra nenhum dado

O motivo quase sempre é o **Device ID** ou o **canal** cadastrados no
dashboard não baterem exatamente com os configurados no ESP32.

- Confira se o Device ID no cadastro da planta é idêntico (maiúsculas,
  minúsculas, espaços) ao configurado no firmware daquele ESP32.
- Confira se o número do canal corresponde à posição certa daquela planta
  no ESP32 (a primeira é 0, a segunda é 1, etc.).

## A bomba ligou, mas a terra continua seca

Isso gera uma notificação automática ("bomba ativa mas a umidade não sobe").
Causas comuns:

- Reservatório de água vazio.
- Mangueira dobrada, entupida ou desconectada.
- Bomba sem energia suficiente (verifique a fonte).
- Sensor de umidade fora da terra ou mal posicionado, dando uma leitura que
  não reflete a realidade.

## As notificações não chegam no celular

- Confira se você tocou em "Ativar notificações" e aceitou a permissão do
  navegador.
- **Se você está acessando o dashboard pelo IP da rede local** (algo como
  `http://192.168.x.x:3000`), alguns navegadores **não permitem** ativar
  notificações push nesse tipo de endereço — eles exigem uma conexão
  segura (HTTPS) ou o endereço `localhost`. Isso é uma limitação do
  navegador, não do sistema. Duas saídas: acessar do próprio computador que
  roda o backend usando `http://localhost:3000`, ou configurar acesso
  remoto com HTTPS (peça ajuda a alguém com experiência técnica para isso).
- Confira se o botão "Ativar notificações" não mostrou nenhuma mensagem de
  erro na hora de clicar.

## O ESP32 não conecta no Wi-Fi

- Confira o nome (SSID) e a senha configurados durante a gravação do
  firmware — são sensíveis a maiúsculas/minúsculas.
- ESP32 só conecta em redes de **2,4GHz** — se o seu roteador tiver uma rede
  de 5GHz com o mesmo nome, tente separar os nomes das duas redes nas
  configurações do roteador.
- Aproxime o ESP32 do roteador para descartar problema de sinal.

## A câmera não tira fotos / trava ao iniciar

- Confira se a fonte de energia da câmera é suficiente (recomendado: fonte
  5V dedicada, separada do adaptador usado só para gravar o firmware).
- Esse é o sintoma mais comum de PSRAM não habilitada corretamente — se você
  regravou o firmware do zero, confirme que usou os arquivos do projeto sem
  alterar as configurações de memória.

## O indicador do dashboard fica "desconectado"

- Confira se o computador/Raspberry Pi que roda o backend está ligado e
  conectado à rede.
- Confira se o celular está na mesma rede Wi-Fi (ou conectado pela VPN, se
  você configurou acesso remoto).
- Recarregue a página do dashboard.

## Uma planta some do dashboard depois de eu remover

Isso é esperado — remover uma planta apaga o histórico de leituras dela.
Não há como desfazer essa ação pelo dashboard.

---

Não encontrou seu problema aqui? Veja as [perguntas frequentes](perguntas-frequentes.md)
ou, se for algo relacionado ao funcionamento interno do sistema, a
documentação técnica em [`doc_agents/`](../doc_agents/) pode ter mais
detalhes (é escrita para agentes de IA, mas serve como referência técnica
também).
