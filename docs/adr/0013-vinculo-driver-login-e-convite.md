# ADR 0013 — Vínculo motorista↔login e convite por e-mail

**Status:** aceito
**Data:** 2026-08-15

## Contexto

O spec 07 (app do motorista) assume que o motorista faz login e vê "meu veículo",
"minha CNH", suas ordens de serviço e seu histórico de viagens. Mas o modelo da Fase 1
separa deliberadamente duas coisas (ver `V3__driver.sql` e `Driver.java`):

- `driver` — **registro operacional** (nome, CNH, telefone), mantido pelo gestor.
- `app_user` (role `MOTORISTA`) — **conta de login**.

Não havia vínculo entre os dois, nem forma de criar um login de motorista (o signup só
cria `GESTOR_FROTA`). Sem resolver "qual `driver` está por trás deste login", **nada** do
spec 07 é implementável — todas as telas e todas as regras de segurança ("filtrar pelo
motorista do token") dependem disso. Esta é a peça de fundação do pacote do app do
motorista; o spec 03 já a antecipava como "fluxo de convite, feature futura".

## Decisões

### O vínculo mora no `driver`, não no `app_user`

`driver.app_user_id` (nullable, `unique`, FK para `app_user`). Nullable porque a maioria
dos motoristas nunca terá login — o registro operacional existe de forma independente.
`unique` garante 1 login por motorista e 1 motorista por login. Fica no `driver` (a
entidade rica de frota) e não no `app_user` (auth genérico) para não vazar conceito de
frota para dentro do módulo de autenticação.

### Convite por e-mail reaproveita o padrão de token já existente

`driver_invite` é idêntico em forma ao `password_reset_token` (V14): hash SHA-256 do
token, nunca o valor cru, curta duração, tabela própria por ter semântica de "usável"
própria. O gestor dispara `POST /v1/drivers/{id}/invite`; o e-mail sai pelo mesmo
`EmailSender` (loga o link em dev, SMTP real quando configurado). Adicionamos
`driver.email` (nullable) para ter para onde mandar o convite e para permitir reconvite.

### A conta nasce habilitada no aceite (o clique é a prova de posse)

`POST /v1/auth/accept-invite {token, senha}` valida o token, cria o `app_user` com role
`MOTORISTA` **já habilitado** no `tenant` do motorista, define a senha e preenche
`driver.app_user_id`. Mesmo raciocínio do `verifyEmail` (ADR 0011): o clique no link
enviado ao e-mail do motorista já é a prova de posse, então não há segundo passo de
confirmação. Se o e-mail já pertencer a um `app_user`, o aceite é rejeitado (a coluna
`app_user.email` é única — não dá para dois logins no mesmo e-mail).

### Aceite na web, login no app (sem deep-link mobile agora)

A tela de aceitar convite/definir senha fica na **web** (reaproveita o padrão visual do
reset-password). O motorista então entra no app mobile pela `LoginScreen` que já existe,
com e-mail + senha. Evita a complexidade de deep-linking mobile neste momento sem perder
nada funcional — migrar para deep-link no app é evolução, não pré-requisito.

### O token de motorista resolve o `driver` server-side, sempre

Um `CurrentDriverResolver` traduz o `JwtPrincipal` (role `MOTORISTA`) no `driver`
correspondente via `app_user_id`, dentro do tenant. Nenhum endpoint do app do motorista
aceita `driverId` vindo do cliente — a identidade vem exclusivamente do token. É a base
das regras de segurança não-negociáveis do spec 07.

## Trade-off aceito

Se o gestor troca o e-mail do motorista depois do vínculo criado, o login continua no
e-mail antigo (o `app_user.email` não é reescrito pelo `driver.email`) — reconciliar isso
é troca de e-mail de conta, fluxo à parte, fora do escopo do convite. Aceito: convite é
para o primeiro acesso, não para gestão contínua de credencial.

## Reavaliar quando

- Houver necessidade de o motorista trocar o próprio e-mail/senha pelo app (hoje só
  reset-password web cobre a senha).
- O onboarding pedir deep-link real no app (aceitar convite abrindo o app direto).
