# 12 — Notificações Operacionais (bot Discord/Telegram)

## Contexto

Feature nova, de uso interno (não é feature de produto pro cliente final): avisar a equipe, num canal de Discord ou Telegram, quando alguém cria conta no sistema e confirma o e-mail. Hoje esse evento não gera nenhum sinal — a única forma de saber é olhar o banco. Em um estágio de poucos clientes, cada signup é um evento que vale reação rápida (acompanhar, entrar em contato, entender de onde veio), e esperar checar o banco manualmente não escala nem faz sentido nesse volume.

Não é uma notificação pro usuário final do produto — é uma notificação operacional interna, categoria diferente do que já existe (alertas de CNH/manutenção pro gestor, notificação push gestor↔motorista, spec 07). Por isso vira spec própria, não uma extensão de nenhuma das duas.

## Desenho — webhook simples, sem infraestrutura nova

**Decisão:** um webhook HTTP disparado pelo `core-api` (dentro de `AuthService`, nos pontos onde `signup` e `verifyEmail` já existem) para um endpoint do bot. Não é uma fila, não é um serviço novo — é uma chamada HTTP simples, assíncrona (não pode atrasar nem falhar a resposta do signup/verify por causa de um bot fora do ar).

Dois eventos disparam notificação:
1. **Conta criada** (`AuthService.signup`, depois de `sendVerificationEmail`) — "novo signup: `{tenantName}` / `{email}`, aguardando confirmação".
2. **E-mail confirmado** (`AuthService.verifyEmail`, depois de `user.setEnabled(true)`) — "conta confirmada: `{tenantName}` / `{email}`".

Por que os dois eventos e não só um: sinaliza tanto o volume de interesse (quantas pessoas começam o cadastro) quanto a conversão real (quantas terminam) — os dois números já são úteis de acompanhar separadamente desde o início, e não custa mais caro notificar os dois do que só um.

## Discord ou Telegram — os dois cabem no mesmo desenho

Tanto Discord quanto Telegram aceitam **webhook de entrada simples** (Discord: "Incoming Webhook" nativo do canal, um POST com JSON; Telegram: `sendMessage` da Bot API, também um POST simples com token do bot + chat ID). Isso significa que não precisa decidir entre os dois agora — o desenho do lado do `core-api` é o mesmo (um `NotificationWebhookSender` com uma implementação por canal), e trocar de canal ou notificar nos dois ao mesmo tempo é configuração, não reescrita.

**Recomendação:** Discord, se não houver preferência forte — o "Incoming Webhook" é mais simples de configurar (uma URL só, sem gerenciar token de bot nem chat ID) e não exige criar um bot de verdade, só ativar um webhook no canal desejado. Telegram fica como alternativa igualmente viável se o canal de acompanhamento já for por lá.

## Modelo de dados e configuração

Não precisa de tabela nova no banco — é configuração, não dado de tenant:
- `OPERATIONAL_WEBHOOK_URL` (variável de ambiente, mesmo padrão já usado para `GEO_SERVICE_TOKEN`/`CORE_JWT_SECRET`): URL do webhook do Discord ou endpoint da Bot API do Telegram.
- Vazio por padrão — sem a variável configurada, a notificação é pulada silenciosamente (mesmo padrão já estabelecido para `MAIL_SMTP_HOST` vazio → `LoggingEmailSender`: degradação explícita, não erro). Isso significa que em dev/local, sem configurar nada, o comportamento é só logar (ou nem logar) em vez de tentar bater num webhook inexistente.

## Falha do bot não pode quebrar o signup

**Regra não negociável:** a chamada ao webhook precisa ser fire-and-forget (assíncrona, com timeout curto, erro capturado e logado — nunca propagado). Se o Discord/Telegram estiver fora do ar, lento, ou a URL estiver errada, o usuário criando conta não pode ser afetado nem perceber diferença nenhuma. Notificação operacional é estritamente secundária ao fluxo principal.

## Definition of Done

- [ ] `NotificationWebhookSender` (interface) com pelo menos uma implementação (Discord ou Telegram, conforme decisão de canal) e uma implementação "no-op"/logging para quando a variável de ambiente não estiver configurada — mesmo padrão de `EmailSender`/`LoggingEmailSender`.
- [ ] `AuthService.signup` dispara notificação de "conta criada" após enviar o e-mail de verificação.
- [ ] `AuthService.verifyEmail` dispara notificação de "conta confirmada" após habilitar o usuário.
- [ ] Chamada ao webhook é assíncrona, com timeout curto e falha nunca propagada pro fluxo de signup/verify.
- [ ] Variável de ambiente documentada em `infra/docker-compose.yml` (mesmo padrão das demais: comentário explicando o que acontece se ficar vazia).
