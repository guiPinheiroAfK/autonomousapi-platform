# ADR 0022 — Responder, editar, excluir, encaminhar e reagir no chat

**Status:** aceito
**Data:** 2026-09-01
**Spec:** `specs/07-app-motorista.md` (mini-chat), complementa ADR 0015

## Contexto

O chat gestor↔motorista (ADR 0015) nasceu texto puro: enviar, ler, e as mensagens
estruturadas de rota (`ATRIBUICAO_ROTA` etc.). Pedido do Guilherme: "abrir um PR do chat"
com as ações que qualquer app de mensagem tem — responder, editar, excluir, encaminhar,
reagir. A pergunta arquitetural real não é "como fazer cada ação" (CRUD comum), é **como
essas ações convivem com a retenção híbrida da ADR 0015**, que já assume que o servidor
esquece mensagem de propósito.

## Decisões

### Editar/excluir/reagir só valem enquanto a mensagem ainda está `ainda_no_servidor`

A ADR 0015 já estabelece que o servidor é canal de entrega + buffer curto — passada a
janela de retenção (~7 dias ou 50 mensagens), `ainda_no_servidor` vira `false` e o poll do
outro lado (`GET .../messages`) para de trazer aquela mensagem. Editar, excluir ou reagir a
uma mensagem fora da janela não teria como propagar pro outro participante: ele nunca mais
busca aquela linha. Em vez de ignorar esse limite, o backend recusa explicitamente
(`ChatMessageActionInvalidException`, 400) e o DTO expõe `stillOnServer` pra a tela já
esconder a ação antes de tentar, sem depender só da mensagem de erro.

### Responder e encaminhar não têm essa restrição — criam mensagem nova

Diferente de editar/excluir/reagir (que mutam uma linha existente), responder e encaminhar
**sempre criam uma linha nova**. A resposta guarda um retrato do texto e do remetente
original no momento do envio (`reply_to_body_snapshot`, `reply_to_sender_user_id`) — não uma
referência viva que precisaria buscar a mensagem original de novo depois, o que quebraria
assim que ela saísse da janela. O único requisito é a mensagem original ainda existir na
consulta feita **agora**, no momento de responder/encaminhar (linha nunca é apagada de
verdade do banco, só marcada — ver próxima decisão), não que ela ainda esteja na janela de
retenção.

### Excluir é soft delete que também esconde o texto na API, não só no banco

Diferente do soft delete já existente (`ainda_no_servidor`, que é sobre sincronização),
excluir por pedido do usuário grava `deleted_at` e o `ChatMessageResponse.from(...)` devolve
`body: null` a partir daí — o texto original continua na linha do banco (auditoria,
consistente com o resto do produto: nada é `DELETE` físico), mas a API nunca mais expõe o
conteúdo. Decisão confirmada com o Guilherme: só **"apagar pra todo mundo"**, sem "apagar só
pra mim" — um modo a menos de estado pra sincronizar entre servidor e IndexedDB local.

### Prazo de 20min pra editar, 35min pra excluir — contado de `sentAt`

Pedido do Guilherme, à parte da decisão acima: editar ou apagar uma mensagem de horas atrás
confunde quem já leu e reagiu ao texto original — não é só sobre a janela de retenção do
servidor. `EDIT_WINDOW` (20min) e `DELETE_WINDOW` (35min) são constantes em `ChatService`,
checadas contra `Instant.now()` vs `message.getSentAt()` nos dois handlers. Excluir tem
prazo maior de propósito: "mandei a mensagem errada, quero apagar" é mais comum passado
algum tempo do que "preciso corrigir o texto". O prazo é **além** da checagem de
`stillOnServer` — as duas guardas coexistem, a mais restritiva vence.

### Reação é upsert de uma linha por (mensagem, pessoa) — não lista de reações por pessoa

`chat_message_reaction` tem `unique (message_id, user_id)`: tocar em um emoji novo troca a
reação anterior da mesma pessoa (delete + insert no mesmo request), tocar de novo no mesmo
emoji remove — igual WhatsApp, nunca duas reações da mesma pessoa na mesma mensagem
simultaneamente. Paleta é fixa (6 emojis Unicode: 👍 ❤️ 😂 😮 😢 🙏), sem SVG customizado nem
picker completo — decisão explícita do Guilherme de manter simples por enquanto,
revisitável se pedirem mais variedade.

## Consequências

- Nenhuma mudança na arquitetura de retenção da ADR 0015 — editar/excluir/reagir só
  herdaram a restrição que já existia, tornando-a explícita em vez de deixá-la um bug
  silencioso (mensagem "editada" que o outro lado nunca vê mudar).
- `chat_message` ganha 6 colunas nullable (`edited_at`, `deleted_at`,
  `reply_to_message_id`, `reply_to_body_snapshot`, `reply_to_sender_user_id`,
  `forwarded_from_message_id`) e uma tabela nova (`chat_message_reaction`) — migration V31.
- Mobile ganhou, na mesma entrega, conserto de layout (uma tela por vez com transição de
  slide, antes duas colunas fixas inutilizáveis em tela estreita) e centralização da coluna
  de mensagens em monitor largo — mudanças de UI, sem decisão arquitetural própria, não
  detalhadas nesta ADR.

## Reavaliar quando

- Pedirem "apagar só pra mim" — hoje deliberadamente fora de escopo, exigiria um segundo
  estado de exclusão por dispositivo (mais complexo que o soft delete global atual).
- O prazo fixo de 20min/35min se mostrar rígido demais na prática (ex. gestor de operação
  maior pedindo prazo configurável por tenant).
- Picker de emoji completo ou reação customizada (SVG) virar pedido recorrente.
