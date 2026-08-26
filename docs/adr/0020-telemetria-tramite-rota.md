# ADR 0020 — Telemetria de uso do trâmite de rota (`route_plan_event`)

**Status:** aceito
**Data:** 2026-08-25
**Spec:** `specs/11-caminho-feliz-rotas.md`
**Levantamento base:** `docs/levantamento-tramite-rota-2026-08-25.md`

## Contexto

O spec 11 pede telemetria sobre o *uso do fluxo* de rota (o gestor confia na sugestão de
ordem? onde as rotas emperram? quanto tempo cada etapa leva?) — não confundir com a
telemetria de prontidão viária da via em si (`road_segment_observation`, spec 02).

## Decisões

### Eventos discretos, não campo calculado

Uma tabela `route_plan_event` — um registro por transição relevante — em vez de agregar
métrica direto no `route_plan`. Mesmo raciocínio já aplicado a `road_segment_observation`:
guardar a observação bruta, agregar depois, nunca calcular só o final direto. Isso permite
reconstruir qualquer métrica futura sem prever de antemão todas as que um dia vão
interessar.

### Schema `core`, não `geo` — corrige a proposta original do spec

O spec 11, como escrito originalmente, propunha schema `geo` ("mesma família conceitual do
pipeline de GPS/observação"). Na prática, isso violaria uma fronteira já estabelecida no
projeto e documentada no próprio código (`RoutePlanService.java`, header):
*"core-api é dono EXCLUSIVO do schema `core`; NUNCA cria/altera objeto do schema `geo`"*.
`route_plan`/`route_stop` — as entidades cujas transições `route_plan_event` registra —
vivem no schema `core`, e é o `core-api` quem detecta e grava cada transição.

Colocar em `geo` exigiria o `core-api` chamar o `geo-api` por HTTP a cada evento (padrão já
usado para ingestão de GPS via `GeoApiClient`) — mas isso introduz uma dependência de rede
e um risco real de perder evento se a chamada falhar, exatamente o oposto do objetivo
("eventos discretos... nunca perde dado"). Schema `core`, gravado na **mesma transação**
que o próprio `route_plan`/`route_stop` muda, garante atomicidade: o evento só existe se
(e sempre que) a mudança de estado realmente aconteceu.

### Taxonomia — decidida a partir do levantamento, não antes dele

Conforme o spec 11 exigia ("decisão explícita por caminho: vira evento ou não"), a lista
final saiu do levantamento completo do trâmite (não só do caminho feliz):

| `tipo` | Quando | `metadado` |
|---|---|---|
| `criada` | `RoutePlanService.create` | — |
| `ordem_sugerida` | `suggestOrder` | `fonte` (`osrm` ou `haversine_fallback`) — resolve o achado do levantamento de que `RouteMatrixService.Matriz.fonte` era calculado e descartado sem chegar no gestor |
| `ordem_ajustada_manualmente` | `create`, quando a ordem confirmada difere da sugerida | nº de posições trocadas |
| `atribuida` | `assignDriver` (chamado tanto pela tela de Rotas quanto por `ChatService.sendRoutePlanMessage`) | `origem` (`tela_rotas` ou `chat`) — resolve o achado de que os dois caminhos hoje notificam de forma diferente sem o gestor saber qual usou |
| `parada_concluida` | `completeStop` | `ordemSugerida`, `ordemReal`, `foraDeOrdem` (bool) |
| `concluida` | `completeStop`, última parada | — |
| `cancelada` | novo endpoint de cancelamento (ver ADR 0021) | `etapa` (`planejada` ou `em_andamento`), `canal` (`tela` ou `chat`) |
| `solicitacao_cancelamento` / `solicitacao_troca_motorista` | motorista, pelo chat (ver ADR 0021) | — |
| `solicitacao_aprovada` / `solicitacao_recusada` | gestor, pelo chat | referência à solicitação original |

`iniciada` (rascunho original do spec 11) **não entra** — decisão tomada junto do
Guilherme: não existe ação explícita de "iniciar rota" no produto (`EM_ANDAMENTO` é efeito
colateral da 1ª parada concluída), e não vamos criar uma agora. A métrica "tempo até
iniciar" é renomeada para o que ela realmente mede ("tempo até a 1ª parada concluída"), sem
mudar o fluxo — revisão de criar uma ação explícita fica para quando (e se) o dado mostrar
que vale a pena.

### Índice pensado para agregação futura, sem agregação implementada agora

`(route_plan_id, tipo, timestamp)` — decisão do Guilherme: só log cru/consulta direta
nesta fatia (sem painel), mas o schema já não precisa de migração quando a agregação virar
prioridade real.

## Consequências

- Fecha a lacuna do levantamento sobre fallback de OSRM invisível e origem de atribuição
  não rastreada — os dois viram `metadado` de eventos que já seriam gravados de qualquer
  forma.
- `route_plan_event` cresce em `core`, não em `geo` — schema `geo` continua exclusivamente
  do `geo-api`, sem exceção aberta pra este caso.

## Reavaliar quando

- Uma métrica pedir agregação pronta (painel pro gestor) — decide-se computar view/tabela
  derivada, sem re-desenhar `route_plan_event` em si.
- Se "iniciar rota" virar ação explícita no futuro, `iniciada` entra na taxonomia sem
  quebrar os tipos já gravados.
