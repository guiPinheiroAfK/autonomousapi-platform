# 0023 — Chat entre membros da equipe

## Contexto

O mini-chat (ADR 0015) sempre modelou só a conversa Gestor↔Motorista — uma linha por par,
`chat_conversation.gestor_user_id`/`driver_id` ambos not-null. Durante uma sessão de suporte
(testar "encaminhar" mensagem), o Guilherme pediu pra também poder conversar com outros
membros da equipe (Despachante, outro Gestor/Admin). Decisão explícita na hora: **qualquer
membro com qualquer outro** do mesmo tenant (não só Gestor↔Despachante) — motorista fica de
fora, ele já tem seu próprio caminho.

## Decisão — aditivo à tabela existente, não um chat paralelo

Em vez de duplicar `chat_conversation`/`chat_message`/reações/responder-editar-excluir-
encaminhar (ADR 0022) num modelo novo, a conversa de equipe é **mais uma linha** na mesma
tabela, com um discriminador (`kind`: `GESTOR_MOTORISTA` default | `EQUIPE`):

- `driver_id`/`vehicle_id` passam a ser nullable — vazios pra `EQUIPE`.
- `gestor_user_id` passa a significar **"participante A"** pra qualquer `kind` (o nome da
  coluna não mudou — reescrever todo lugar que já lê `getGestorUserId()` não valia o
  ganho de clareza, e o Javadoc da entidade documenta o duplo sentido).
- Nova coluna `participant_b_user_id` — só usada quando `kind = EQUIPE`.
- Par sempre ordenado (menor UUID em `gestor_user_id`, maior em `participant_b_user_id`) —
  garante uma linha só por par, não duas, independente de quem inicia a conversa.

**Por que isso funciona sem tocar em quase nada:** `chat_message`, reações,
responder/editar/excluir/encaminhar (ADR 0022), retenção híbrida (ADR 0015) e o job de
limpeza operam todos sobre `conversation_id` — nenhum deles sabe ou precisa saber que existe
mais de um "tipo" de conversa. O único lugar que precisou de lógica nova foi resolver "quem é
o outro participante" (`ChatService.otherParticipantUserId`), usado pra push e pro
"digitando" — centralizado num método só, chamado nos mesmos pontos que antes tinham a lógica
gestor/motorista *inline* e duplicada.

## O que fica de fora, de propósito

- **Ações de rota (anexar/cancelar/trocar) só existem no chat gestor↔motorista.**
  Conversa de equipe não tem motorista pra atribuir/notificar — os endpoints
  `POST .../route-plan`, `.../route-plan/cancel`, `.../route-plan/troca` recusam com erro
  explícito (`ChatService.requireGestorMotorista`) se chamados numa conversa `EQUIPE`, em vez
  de deixar `RoutePlanService` falhar de forma obscura com "motorista não encontrado"
  (`driverId` nulo).
- **App nativo do motorista não ganha essa tela** — motorista não participa de conversa de
  equipe (fora do escopo por decisão do produto, não só técnico).
- **Limite de retenção do servidor pode não se aplicar de fato.** O job de limpeza
  (`ChatCleanupJob`) só age depois que o "participante A" confirma sync via
  `chat_sync_cursor` — endpoint hoje só chamado pelo web app do Gestor. Numa conversa de
  equipe onde o Gestor é participante A, funciona igual a antes; numa conversa entre dois
  Despachantes (ou onde o Despachante é participante A), ninguém chama `sync-cursor` — a
  limpeza simplesmente nunca roda pra essa conversa, e as mensagens continuam sempre visíveis
  via `listMessages` (nenhuma sai da janela). Falha segura (nunca perde dado antes da hora),
  mas retenção "ilimitada de fato" é uma limitação conhecida, registrada aqui — não bloqueou
  esta entrega porque o volume de chat interno é baixo, e resolver direito exigiria
  generalizar `chat_sync_cursor` pra qualquer papel, escopo maior que o pedido original.

## Quem pode iniciar conversa com quem

`GET /v1/chat/team-members` lista quem dá pra chamar (mesmo tenant, habilitado, qualquer
papel exceto MOTORISTA, exceto o próprio usuário) — aberto a GESTOR_FROTA/ADMIN/DESPACHANTE/
VISUALIZADOR (os quatro podem iniciar e responder, sem hierarquia). `POST
/v1/chat/team-conversations` (`{otherUserId}`) abre ou recupera a conversa, idempotente pelo
par ordenado.
