# ADR 0021 — Cancelamento de rota, reatribuição e solicitação pelo motorista

**Status:** aceito
**Data:** 2026-08-25
**Spec:** `specs/11-caminho-feliz-rotas.md`
**Levantamento base:** `docs/levantamento-tramite-rota-2026-08-25.md` (gaps 2, 3, 4)

## Contexto

O levantamento do trâmite achou que cancelamento, edição e reatribuição/desatribuição não
existem hoje — nem endpoint, nem UI, e edição é bloqueada até no ORM (`updatable = false`).
Isso é o gap nº 2 e 3 de prioridade (depois de "app mobile não mostra rota atribuída",
ADR pendente à parte).

## Decisões

### Cancelamento assimétrico por status — não é a mesma ação nos dois estados

- **`PLANEJADA`** (antes de qualquer parada concluída): gestor cancela **direto**, pela
  tela de Rotas — ação simples, sem necessidade de passar pelo motorista, já que ele ainda
  não começou nada.
- **`EM_ANDAMENTO`** (já tem parada concluída): gestor **só cancela pelo chat**, não pela
  tela de Rotas. Reaproveita o padrão já existente (`ChatService.sendRoutePlanMessage`
  mistura mensagem estruturada + ação real) — cancelar no meio do trâmite é uma decisão que
  precisa ficar registrada na conversa com o motorista, não um botão isolado numa lista.

Paradas já concluídas não são desfeitas — o histórico fica, a rota vira `CANCELADA`.

### Motorista não cancela nem reatribui sozinho — ele **solicita**

Decisão central desta ADR: o motorista não tem autoridade unilateral sobre cancelamento
nem sobre trocar de motorista (ex. "quero passar essa rota pra outra pessoa"). Ele manda
uma **solicitação estruturada pelo chat** (mesmo padrão de `ATRIBUICAO_ROTA` —
`ChatMessageType` ganha `SOLICITACAO_CANCELAMENTO` e `SOLICITACAO_TROCA_MOTORISTA`), e o
gestor aprova ou recusa, também pelo chat.

**Enquanto a solicitação está pendente, a rota continua ativa normalmente** — motorista
segue concluindo parada, nada trava. Solicitação pendente é informativa pro gestor, não um
gate no fluxo operacional. Consistente com o princípio já usado em todo o produto (falha
de notificação nunca bloqueia o fluxo principal; SMTP/push fora do ar degradam, não travam):
uma decisão do gestor pendente não pode ser diferente.

### `RoutePlanStatus` ganha `CANCELADA`

Novo valor no enum, ao lado de `PLANEJADA`/`EM_ANDAMENTO`/`CONCLUIDA`. `avancarStatus`
continua sendo o único método que escreve `status` — cancelamento passa por ele também, não
abre um segundo caminho de escrita.

### Reatribuição usa o mesmo `assignDriver`, com uma trava a menos

Hoje `assignDriver` lança `RoutePlanAlreadyAssignedException` sempre que já há motorista
diferente — decisão antiga documentada como "nunca sobrescreve silenciosamente". Fica
mantida para o caminho **direto** (tela de Rotas). Mas o novo caminho — gestor aprovando uma
`SOLICITACAO_TROCA_MOTORISTA` pelo chat — precisa poder reatribuir de propósito. Resolução:
`assignDriver` ganha um parâmetro `forcar` (default `false`, mantendo o comportamento atual
em todo o resto do código); só o handler de aprovação de troca no `ChatService` passa
`true`, e mesmo assim só quando a aprovação corresponde a uma solicitação real registrada —
nunca um `forcar=true` solto em outro lugar.

### Corrige junto: as duas race conditions achadas na revisão de código

Como esta ADR já mexe exatamente em `completeStop` e `assignDriver` (pra adicionar
cancelamento e o novo parâmetro de reatribuição), as duas race conditions achadas na
revisão ficam corrigidas na mesma passada, em vez de reabrir os mesmos métodos depois:

- **`completeStop`**: duas conclusões de parada quase simultâneas podiam travar a rota em
  `EM_ANDAMENTO` para sempre (cada transação lia o snapshot de paradas concluídas antes da
  outra commitar). Corrigido com lock pessimista (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)
  na busca do `RoutePlan` por id, serializando conclusões concorrentes da mesma rota.
- **`assignDriver`**: duas atribuições concorrentes podiam sobrescrever uma à outra sem
  lançar a exceção que o próprio código promete nunca deixar acontecer. Mesmo lock
  pessimista resolve — a segunda transação só lê `driverId` depois que a primeira
  commitou, então o guard existente (`driverId != null && != driverId`) passa a funcionar
  de verdade sob concorrência, não só em teste sequencial.

### Data de execução: fixa fuso `America/Sao_Paulo`, não passa a ser por tenant

A validação `dataExecucao.isBefore(LocalDate.now())` usava o fuso padrão do container (sem
`TZ` configurado em lugar nenhum, cai em UTC) em vez do fuso do gestor. Decisão do
Guilherme: a operação é só Brasil por enquanto — fixa `ZoneId.of("America/Sao_Paulo")`
explícito no código (mais robusto que só configurar `TZ` no Dockerfile, que dependeria de
infra estar certa em todo ambiente) em vez de introduzir fuso por tenant, que seria escopo
maior sem necessidade real hoje.

## Consequências

- Fecha os gaps 2, 3 e 4 do levantamento (cancelamento, edição parcial via
  recriar-já-que-cancela, reatribuição).
- Edição de rota (mudar parada/data depois de criada) continua fora de escopo — paliativo
  aceito é cancelar (agora possível) e recriar.
- Duas correções de bug de concorrência entram "de carona" nesta ADR, documentadas aqui em
  vez de merecer ADR própria — são fix, não decisão arquitetural nova.

## Reavaliar quando

- Edição de rota já atribuída virar pedido recorrente (hoje o paliativo "cancela e recria"
  é aceito, mas gera duas linhas na listagem de rotas em vez de uma).
- O volume de solicitações pendentes justificar uma tela própria de "solicitações", em vez
  de aparecerem só dentro da conversa do chat.
