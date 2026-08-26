# 11 — Caminho Feliz de Pontos de Coleta/Entrega/Rotas (foco principal)

## Por que este é o foco principal, não só mais uma tela

O modelo de dados de rota multi-parada (`route_plan`/`route_stop`/`collection_point`, spec `02-dados-mapas-rotas.md`) já existe. O que ainda não foi tratado como prioridade é o **fluxo operacional completo**, ponta a ponta, funcionando sem atrito — do gestor montando uma rota até o motorista concluindo ela. Isso importa mais do que parece porque é exatamente onde a maioria das empresas de frota (no Brasil e fora) erra na prática: não é falta de tecnologia de roteamento, é o processo do dia a dia ficar cheio de atrito (WhatsApp, planilha, "liga pro motorista pra confirmar") que faz ninguém confiar no sistema e todo mundo voltar pro jeito manual em duas semanas.

Não é estética. É garantir que cada etapa do fluxo é óbvia, rápida e confiável o suficiente para o gestor preferir usar o sistema a fazer do jeito velho.

## O caminho feliz, etapa por etapa

1. **Gestor monta a rota** — escolhe pontos (cadastrados via `collection_point` ou avulsos), data de execução, categoria (`ROTA` ou `TRANSFER`).
2. **Sistema sugere ordem** — heurística sobre distância real (OSRM `/table`, spec 02) devolve sequência sugerida. Gestor pode aceitar ou reordenar manualmente (estilo "Google Maps": sugestão é ponto de partida, não decisão automática).
3. **Rota é atribuída ao motorista** — via designação direta (`driver_vehicle_assignment`) ou pela mensagem estruturada no chat (`atribuicao_rota`, spec 07).
4. **Motorista executa** — vê a rota no app (lista de paradas pra `ROTA`, cartão único pra `TRANSFER`, spec 02/07), marca cada parada como concluída conforme avança (decisão já tomada: "visualiza e marca concluído", não fluxo cego).
5. **Rota conclui** — sistema registra ordem real executada ao lado da ordem sugerida (campo já existe em `route_stop`, spec 02) e, se `TRANSFER`, calcula `margem_realizada` (spec 10).

**Definição de "caminho feliz" pra efeito deste documento:** as 5 etapas acima acontecendo sem o gestor precisar sair do sistema (ligar, mandar mensagem fora do chat interno, checar manualmente se o motorista viu) e sem o motorista ficar em dúvida sobre o que fazer a cada tela.

## O que auditar — ampliado: todo o trâmite, não só o caminho feliz

**Decisão (2026-08-25):** a primeira entrega não é código — é um levantamento estruturado
de **todos os caminhos** do trâmite de rota, não só as 5 etapas do caminho feliz. Pra
`ROTA` e `TRANSFER`, mapear também:

- **Cancelamento em qualquer etapa** — antes de atribuir, depois de atribuída, já em
  andamento (com paradas parcialmente concluídas).
- **Reatribuição** — trocar o motorista de uma rota já atribuída (com ou sem execução
  iniciada).
- **Edição de rota já atribuída/iniciada** — mudar parada, horário, veículo depois que já
  saiu do estado "planejada".
- **Parada concluída fora da ordem sugerida** — o que o sistema faz com o desvio (bloqueia,
  permite e registra, etc.).
- **Motorista que não confirma nada por tempo indefinido** — o que existe hoje (nada) e o
  que deveria existir.
- **ROTA vs. TRANSFER**: cada ramo acima se comporta igual nos dois tipos, ou algum exige
  tratamento próprio (`TRANSFER` já é cartão único, sem lista de paradas — pode ter sua
  própria pegadinha em cancelamento/reatribuição).

Além dos 4 pontos originais do caminho feliz (ainda válidos, agora como parte do
levantamento maior):

- **Estados vazios/de erro mal resolvidos** — o que a tela mostra quando não há pontos cadastrados ainda, quando o OSRM está fora do ar (spec 02 já prevê fallback pra haversine, mas o gestor *vê* que isso aconteceu, ou é silencioso pra ele?), quando uma rota fica sem motorista disponível.
- **Fricção na etapa 2 (ajuste manual da ordem)** — reordenar é arrastar-e-soltar, é subir/descer por botão, é editar número? Quanto mais manual/lento for esse passo, menos o gestor confia na sugestão e mais ele volta a decidir tudo por fora.
- **Visibilidade da etapa 4 pro gestor** — quando o motorista marca uma parada como concluída, o gestor vê isso em tempo real (ou próximo disso) no painel, ou só descobre depois? Uma rota "travada" no meio (motorista não abriu o app, não confirmou nada) precisa aparecer pro gestor de algum jeito — hoje não há esse sinal.
- **Transição 3→4** — depois de atribuída, o motorista recebe notificação de verdade (push, spec 07 item 5) ou só fica disponível se ele abrir o app por conta própria?

**Formato do levantamento** (decisão explícita do Guilherme):
- **Diagrama** (Mermaid) do trâmite completo — a linha feliz e todos os ramos de exceção,
  como estados/transições do `route_plan` (e do `route_stop` quando fizer diferença).
- **Estado atual no código**, por caminho — o que já existe, o que não existe, o que existe
  mas com atrito.
- **"Conversas de texto"** — pra cada tela/estado-chave (inclusive os de exceção: modal de
  cancelamento, aviso de reatribuição, alerta de rota parada), o texto/copy real que o
  usuário vê.
- **Decisão explícita por caminho mapeado: vira um tipo de evento em `route_plan_event`, ou
  não?** — a taxonomia de eventos (rascunho na seção de telemetria abaixo) sai deste
  levantamento, não está fechada antes dele.

Essa auditoria é o próximo passo concreto antes de qualquer linha de código nova — sem ela, "melhorar o caminho feliz" fica vago demais pro Claude Code implementar.

**✅ Feito (2026-08-25):** [`docs/levantamento-tramite-rota-2026-08-25.md`](../docs/levantamento-tramite-rota-2026-08-25.md) — diagrama do trâmite, os 12 caminhos mapeados contra o código real, taxonomia de eventos e gaps priorizados. Achados que mudam o escopo da implementação:

- **O app mobile não mostra a rota atribuída.** A aba "Rota" é um buscador de endereço genérico; `apps/mobile/src/api/client.ts` não tem nenhuma referência a `routePlans`. A etapa 4 do caminho feliz só funciona no painel web — o motorista precisa abrir o navegador, não o app feito pra ele. É o gap bloqueante nº 1.
- **Cancelar, editar, reatribuir e desatribuir não existem** — nem endpoint, nem UI; edição é bloqueada até no ORM (`updatable = false`), e reatribuição lança exceção por decisão consciente, mas sem alternativa explícita.
- **Atribuir pela tela de rotas não notifica o motorista; atribuir pelo chat notifica.** Dois caminhos, comportamentos diferentes, sem o gestor saber.
- **O backend já sabe se a sugestão de ordem veio degradada** (`RouteMatrixService.Matriz.fonte`), mas descarta essa informação antes de responder ao gestor.
- **Uma das 3 métricas pedidas abaixo não é mensurável como definida**: não existe momento de "iniciar rota" — `EM_ANDAMENTO` é efeito colateral da primeira parada concluída.

## Telemetria de uso do fluxo (não é a mesma telemetria de prontidão viária)

Pedido explícito: começar a tratar coleta de dado *sobre o uso do fluxo em si* desde já — não confundir com o pipeline de GPS/`road_readiness_score` (spec 02), que é sobre a via. Isso aqui é sobre o processo: o gestor confia na sugestão? Onde as rotas emperram? Quanto tempo cada etapa leva na prática?

**Decisão de modelo:** uma tabela de eventos, `route_plan_event` (schema `geo`, ao lado de `route_plan`) — um registro por transição relevante no ciclo de vida da rota, não um campo calculado. Escolha deliberada: eventos discretos permitem reconstruir qualquer métrica depois (tempo entre etapas, taxa de reordenação, taxa de abandono) sem precisar prever de antemão toda métrica que um dia vai interessar — o mesmo raciocínio já aplicado a `road_segment_observation` (spec 02): guardar a observação bruta, agregar depois, nunca calcular só o agregado final direto.

Campos: `route_plan_id`, `tipo`, `timestamp`, `ator` (gestor ou motorista, referência ao usuário), `metadado` (jsonb — ex. em `ordem_ajustada_manualmente`, quantas posições mudaram em relação à sugestão; em `parada_concluida`, se a ordem real bateu com a sugerida ou não).

**`tipo` — rascunho, não fechado.** Lista inicial: `criada`, `ordem_sugerida`, `ordem_ajustada_manualmente`, `atribuida`, `iniciada`, `parada_concluida`, `concluida`, `cancelada`. A lista definitiva sai do levantamento da seção acima — cada caminho mapeado (cancelamento em cada etapa, reatribuição, edição pós-atribuição, parada fora de ordem, rota sem reação do motorista) precisa de uma decisão explícita se vira um `tipo` novo (candidatos prováveis: `reatribuida`, `editada_apos_atribuicao`, `parada_concluida_fora_de_ordem`) ou se cabe no `metadado` de um tipo já existente.

**Métricas que isso destrava, sem precisar de tabela nova depois:**
- Taxa de aceitação da sugestão de ordem (rotas concluídas sem nenhum evento `ordem_ajustada_manualmente` ÷ total) — termômetro direto de quão boa a heurística está sendo na prática, mais honesto que qualquer métrica teórica de distância percorrida. ⚠️ Exige que `create` receba a ordem sugerida original pra comparar com a confirmada — hoje ela não chega no backend (o gestor reordena no cliente e só a lista final é enviada).
- Tempo médio entre `atribuida` e `iniciada` — mede se o motorista está vendo/reagindo à atribuição rápido ou se fica parada horas. ⚠️ **Não mensurável como definida hoje:** não existe ação de "iniciar rota" — `PLANEJADA → EM_ANDAMENTO` é efeito colateral da primeira parada concluída, então isso mediria "atribuída até a primeira parada concluída", que embute o deslocamento inteiro até o primeiro ponto. Decisão pendente na Fase C: renomear a métrica pro que ela mede, ou criar a ação explícita de iniciar (ver levantamento, itens 12 e "Impacto na telemetria").
- Rotas com `data_execucao` vencida sem nenhum evento além de `criada`/`atribuida` — sinal direto de rota "esquecida", útil como alerta futuro pro gestor (não precisa implementar o alerta agora, só que o dado já existe pra isso quando for priorizado).

**Por que schema `geo` e não `core`:** é dado de uso/operação, mesma família conceitual do pipeline de GPS/observação — mantém a mesma fronteira já estabelecida no projeto (schema `core` é cadastro/regra de negócio transacional; schema `geo` é dado observacional que vira insumo de análise depois).

## Perguntas — respondidas pelo Guilherme (2026-08-25)

1. **Prioridade da auditoria vs. já sair implementando `route_plan_event`?** ✅ Auditoria primeiro (seguiu a recomendação) — e ampliada pra cobrir todos os ramos do trâmite, não só o caminho feliz (ver seção acima).
2. **Alguma das lacunas já é conhecida de cabeça?** Nenhuma apontada de antemão — o levantamento cobre tudo do zero, incluindo um diagrama (Mermaid) e o texto/copy real de cada tela-chave ("conversas de texto") antes de qualquer código.
3. **Quem vê a telemetria de uso do fluxo?** ✅ Só log cru/consulta direta por enquanto (seguiu a recomendação) — mas o schema já é desenhado pensando na agregação futura (índice em `route_plan_id`+`tipo`+`timestamp`, `metadado` com formato consistente por tipo), pra não precisar migrar dado quando o painel virar prioridade de verdade.

## Definition of Done (primeira fatia)

- [x] Levantamento completo do trâmite documentado — caminho feliz + cancelamento (todas as etapas) + reatribuição + edição pós-atribuição + parada fora de ordem + rota sem reação do motorista, para `ROTA` e `TRANSFER`, com diagrama Mermaid e "conversas de texto" por tela-chave. → [`docs/levantamento-tramite-rota-2026-08-25.md`](../docs/levantamento-tramite-rota-2026-08-25.md)
- [x] Taxonomia final de `route_plan_event.tipo` definida a partir do levantamento acima (não antes dele) — ver seção "Taxonomia de eventos proposta" no levantamento. Sete tipos implementáveis já (`criada`, `ordem_sugerida`, `ordem_ajustada_manualmente`, `atribuida`, `parada_concluida`, `concluida`), três bloqueados por caminho inexistente (`cancelada`, `reatribuida`, `editada`), um condicional (`iniciada`) e um descartado (`parada_reaberta`).
- [ ] `route_plan_event` modelado e sendo gravado nas transições já existentes no backend — mesmo sem nenhuma tela nova consumindo ainda.
- [ ] Pelo menos as 3 métricas listadas acima calculáveis por query direta (não precisa de dashboard ainda).
- [ ] Gaps identificados no levantamento priorizados e viram itens novos em `08-decisoes-tecnicas-pendentes.md` ou specs próprias, conforme o tamanho de cada um.
