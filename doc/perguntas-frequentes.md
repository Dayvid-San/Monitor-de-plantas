[← Voltar ao índice](README.md)

# Perguntas frequentes

**Preciso saber programar para usar o sistema no dia a dia?**
Não. Programar e mexer em terminal só é necessário na instalação inicial
(gravar o firmware nos ESP32 e subir o backend). Depois de instalado, tudo
se controla pelo dashboard no navegador.

**Preciso de internet para o sistema funcionar?**
Não. Tudo roda na sua rede Wi-Fi de casa — sensores, backend e dashboard se
comunicam localmente. Internet só é necessária se você quiser acessar o
dashboard de fora de casa (veja a etapa 6 da [instalação](instalacao.md)).

**Posso usar sem a câmera?**
Sim. A câmera é opcional — sem ela, você ainda tem umidade do solo,
temperatura, umidade do ar, luz e rega automática. Só não terá o selo de
saúde baseado em foto.

**Posso ter mais plantas do que um ESP32 aguenta?**
Sim — basta montar um segundo kit de sensores com outro Device ID e
cadastrar as plantas dele normalmente no mesmo dashboard. Não há limite de
quantos kits você pode ter.

**O selo "Crítico" na foto significa que a planta está doente?**
Não necessariamente um diagnóstico — é uma estimativa simples baseada em
quanto da folhagem aparece verde na foto (menos verde, mais amarelo/marrom,
pior a nota). Serve como um alerta para você dar uma olhada, não substitui
seu próprio julgamento.

**Uma planta pode compartilhar sensor de temperatura/luz com outra?**
Sim — o sensor de temperatura/umidade do ar e o de luz são compartilhados
entre todas as plantas ligadas ao mesmo ESP32 (só a umidade do solo é
individual, um sensor por vaso).

**O que acontece se o backend (computador) ficar desligado por um tempo?**
A rega automática continua funcionando normalmente — essa decisão é tomada
pelo próprio ESP32, não pelo backend. O que para de funcionar enquanto o
backend está desligado é o dashboard, o histórico e as notificações.

**Posso mudar os limiares de umidade depois de já ter cadastrado a planta?**
Sim, a qualquer momento, pelo botão "Limiares" no card da planta — veja
[Usando o dashboard](usando-o-dashboard.md).

**Quanto tempo demora entre eu mandar "Regar agora" e a bomba realmente ligar?**
Poucos segundos — o ESP32 verifica por comandos pendentes em um intervalo
curto (por padrão, a cada 8 segundos).
