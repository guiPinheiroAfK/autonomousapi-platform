# ADR 0016 — Notificações push

**Status:** aceito (design travado; implementação na PR de push)
**Data:** 2026-08-15

## Contexto

O spec 07 pede notificação push para 4 eventos: CNH vencendo, manutenção agendada, aviso
do gestor e nova mensagem de chat. Push real depende de credencial de provedor
(FCM/APNs/Expo) — a mesma situação da Stripe e do SMTP no projeto.

## Decisões

### Abstração `PushSender`, entrega real bloqueada por credencial

Mesmo padrão do `EmailSender` (ADR 0011) e do `BillingService` com a Stripe: uma interface
`PushSender` com duas implementações escolhidas por config. `LoggingPushSender` (padrão
dev/demo) loga a notificação em vez de enviar — permite testar todo o fluxo (evento →
push disparado) sem credencial nenhuma. A implementação real (Expo Push, dado que o app é
Expo/React Native) entra quando houver credencial configurada. Assim o mecanismo fica
100% pronto e testável agora; só a entrega física é que espera a credencial.

### Registro de device token

`push_device_token` (user_id, token, plataforma, criado_em). O app registra o token do
dispositivo ao logar; o disparo busca os tokens do usuário destinatário. Token repetido é
upsert (um dispositivo, um token vigente).

### Eventos por gatilho vs. por job

- **CNH vencendo / manutenção agendada**: job periódico (reaproveita a mesma lógica de
  alerta que o painel do gestor já calcula), dispara push com antecedência configurável.
- **Aviso do gestor / nova mensagem de chat**: disparo no momento da escrita (síncrono ao
  POST que originou o evento).

### Falha de envio não derruba a operação

Igual ao `EmailSender`: se o push falhar, loga alto (sinal de provedor mal configurado) e
segue — a operação que originou o evento (salvar mensagem, rodar o job) não é revertida por
falha de notificação.

## Reavaliar quando

- Houver credencial de provedor para plugar a implementação real.
- O volume pedir agrupamento/rate-limit de push (ex. não notificar 10x seguidas).
