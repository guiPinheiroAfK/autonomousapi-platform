# Relatório — polimento de UI + i18n + limpeza de código (sessão de 2026-08-23/24)

Sessão longa em Sonnet 5, sem troca de modelo (nenhum dos passos exigiu arquitetura
delicada o bastante para justificar Opus — sinalizado ao usuário e não usado).

**Continuação (mesmo dia):** testes automatizados pra `apps/web` (gap identificado numa
revisão de status pós-cleanup) e paginação dos dois endpoints de maior volume — ver
"Fase 9" no fim deste documento.

## Resumo

Sete fases de polimento visual/UX em `apps/web` (Motion, microinterações, gráficos, marca,
responsividade mobile, landing page, i18n PT/EN/ES) seguidas de uma fase de limpeza:
N+1 de banco, índices faltando, dead code e lógica duplicada — com ênfase em desempenho de
banco, a pedido explícito do usuário.

## Fases 0–5: Motion, microinterações, gráficos, marca, mobile, landing

**O que mudou:** `motion/react` (Motion) substituindo animações CSS soltas; toasts via
Sonner; `AlertDialog` do Radix para confirmação. Sidebar virou drawer off-canvas em mobile
(`AppShell`/`Sidebar`/`Topbar`), fechando sozinha na troca de rota. Landing page ganhou
watermark de marca, `Reveal`/`RevealGroup`/`RevealItem` (entrada por scroll via
`whileInView`) e o `DonutChart` real na Vitrine (antes uma barra de status estática).
Logo (`favicon.svg`) corrigido: erro de parse XML (`--` ilegal dentro de comentário) e
desalinhamento geométrico dos retângulos da via em relação ao ponto de fuga.

**Bug real encontrado — Cascade Layers:** `apps/web/src/index.css` tinha um reset global
(`* { border-color: var(--color-border); } `) fora de qualquer `@layer`. Em CSS, regra
sem `@layer` sempre vence regra dentro de `@layer` — independente de especificidade ou
ordem — então essa regra sobrescrevia silenciosamente toda cor de borda definida via
utilitário Tailwind (`border-[var(--x)]`) no app inteiro. Corrigido envolvendo o reset em
`@layer base`. Raiz encontrada via `curl` no CSS compilado + `getComputedStyle` no
navegador, comparando o valor aplicado com os dois tokens candidatos.

**Onde:** `src/components/shared/Logo.tsx`, `Reveal.tsx` (novo), `LandingPage.tsx`,
`index.css`, `components/layout/{AppShell,Sidebar,Topbar}.tsx`, `public/favicon.svg`.

## Fase 6: i18n (PT-BR / EN / ES)

**O que mudou:** `react-i18next` + `i18next-browser-languagedetector`, com todas as 26
páginas e componentes compartilhados de alto uso (`StatusBadge`, `BuscaEndereco`)
convertidos de string PT hardcoded para `t('namespace.chave')`. `LanguageSwitcher` no
Topbar. Recursos em `src/i18n/locales/{pt,en,es}.json`.

**Bug real encontrado — `nonExplicitSupportedLngs`:** a config inicial usava
`supportedLngs: ['pt-BR', 'en', 'es']` + `nonExplicitSupportedLngs: true`. Isso quebrava
`t()`/`exists()` silenciosamente para toda chave fora do namespace `landing`/`auth` — bug
isolado via um reprodutor Node.js isolado, fora de React/browser, pra eliminar variáveis.
Corrigido trocando para `load: 'languageOnly'` + chaves de recurso na língua base (`pt`,
não `pt-BR`).

**Onde:** `src/i18n/` (novo), todas as páginas em `src/pages/`, `StatusBadge.tsx`.

## Fase 7: polimento mobile profundo

**O que mudou:**
- `components/ui/modal.tsx` reescrito como bottom sheet arrastável (Motion `drag="y"`,
  `dragElastic`, fecha com swipe-down ou velocidade). `dialog.tsx` ganhou o mesmo
  comportamento responsivo (sobe do rodapé em mobile, centralizado em desktop).
- **Correção de zoom automático do iOS Safari:** todo campo de formulário com
  `font-size` computado abaixo de 16px faz o Safari iOS dar zoom na página inteira ao
  focar o campo. `Input`, `Select`, os dois `<textarea>` de `WorkOrdersPage`/
  `DriverMorePage`, e o `CampoPublico` de `AuthLayout` (usado em Login/Signup/
  ForgotPassword/ResetPassword/AcceptInvite) foram trocados de `text-sm`/`text-[15px]`
  para `text-base sm:text-sm` (ou equivalente) — 16px+ no mobile, tamanho original no
  desktop via breakpoint `sm:`.
- Alvos de toque de botões "ver detalhe" (ícone de olho) em tabelas bumped de 28px
  (`size-7`) pra 32px (`size-8`) em `VehiclesPage`, `DriversPage`, `WorkOrdersPage`.

**Limitação conhecida, não corrigida:** `ChatPage.tsx` usa um layout de sidebar fixa
(`w-72 shrink-0`, sem breakpoint responsivo) — não quebra, mas não foi desenhado pra
mobile. Fica registrado aqui como próximo candidato de uma fase de mobile polish futura,
em vez de forçar um redesenho não pedido nesta sessão.

**Onde:** `components/ui/{modal,dialog,input,select}.tsx`,
`components/layout/AuthLayout.tsx`, `pages/{WorkOrders,DriverMore}Page.tsx`.

## Fase 8: limpeza de código (banco, dead code, duplicação)

Auditoria feita por dois subagentes de exploração (um em `services/core-api`, um em
`apps/web`) antes de qualquer edição, pra achar os pontos de maior alavancagem em vez de
mexer por mexer. **Itens de contrato de API (paginação) foram identificados mas
deliberadamente NÃO alterados** — ver seção "Pendências" abaixo.

### Backend — banco de dados (core-api)

| Onde | Problema | Correção |
|---|---|---|
| `RoutePlanService.listForGestor` | N+1: pra cada rota, 3 queries (motorista, veículo, paradas) — `GET /v1/route-plans` fazia até 3N+1 | Novo `toResponses(List)` batch: `findAllById` pros motoristas/veículos, `RouteStopRepository.findAllByRoutePlanIdInOrderByOrdemSugeridaAsc` pras paradas, tudo agrupado em memória |
| `ChatService.listConversations` | N+1: 3 queries por conversa (motorista, veículo, última mensagem) — endpoint fica em poll (spec 07), custo composto | Novo `toResponses(List)` batch com `ChatMessageRepository.findAllByConversationIdInAndAindaNoServidorTrueOrderBySentAtDesc` (janela de retenção já é curta — 7 dias/50 msgs — trazer tudo e escolher a mais recente por conversa em memória é barato) |
| `WorkOrderService.list` / `maintenanceSummary` | Carregava a frota e a equipe **inteiras** do tenant pra popular um Map, mesmo quando a lista de OS referenciava só uma fração | `vehicles.findAllById(...)`/`drivers.findAllById(...)` escopados aos IDs realmente presentes no resultado |
| `ExpenseEntryService.exportCsv` | Mesmo padrão: carregava todos os veículos do tenant antes de saber quais apareciam nas despesas | Inverteu a ordem: busca as despesas primeiro, escopa `findAllById` aos veículos referenciados |
| 18 services / ~33 chamadas | `repo.findByX(...).orElseThrow(() -> new NotFoundException(msg))` repetido quase mecanicamente | `Lookups.orNotFound(Optional, message)` (novo, em `core/error/`) — mesmo comportamento, sem repetição |
| `vehicle.proxima_manutencao_data`/`_km`, `driver.cnh_validade` | `AlertPushJob` varre TODA a base diariamente sem índice — sequential scan crescente | Migration `V25`: índices parciais (`where ... is not null`) |
| `trip(tenant_id, user_id, started_at)` | `TripService.list` filtra pelos dois e ordena pela terceira; só havia índice single-column por coluna | Migration `V25`: índice composto |

**Deixado como está, com justificativa:** `BudgetService`/`BudgetAlertJob` também fazem
uma query de agregação por orçamento (não em lote) — mas cada orçamento tem um escopo
diferente (veículo/categoria), então não dá pra virar uma única query `GROUP BY` sem gerar
SQL bem mais complexo; o job roda uma vez por dia e a cardinalidade de orçamentos por
tenant é tipicamente pequena. Risco/benefício não compensou pra este cleanup.

### Frontend (apps/web)

| Onde | Problema | Correção |
|---|---|---|
| `lib/workOrderLabels.ts`, `components/shared/VehicleTypeIcon.tsx`, `lib/format.ts` | `TIPO_OS_LABEL`, `VEHICLE_TYPE_LABEL`, `formatKm` — sobras da migração de i18n (Fase 6), sem nenhuma referência restante | Removidos |
| 6 páginas (`Drivers`, `Costs` ×2, `WorkOrders`, `VehicleCosts`, `Vehicles`) | `handleDelete` repetia linha a linha: confirmar → deletar → toast → refresh | `deleteWithConfirm()` novo em `lib/confirm.tsx`, ao lado do já existente `confirmDialog` |
| `AffiliatesPage`, `BillingPage`, `ChargingStationsPage` | `useEffect` de fetch-on-mount tinha `t` (função de tradução) na dependência — trocar de idioma no `LanguageSwitcher` disparava um refetch desnecessário da lista | Dependência trocada para `[]` (busca só uma vez; comentário explica o motivo) |

**Considerado e descartado:** os `card.tsx` (`CardDescription`/`CardContent`/
`CardFooter`) e `dialog.tsx` (`DialogTrigger`) não usados hoje **não foram removidos** —
são a metade "conjunto" de uma família de primitivas shadcn-style já usada em todo o app
(`Card`/`CardHeader`/`CardTitle`), não sobra de migração. Remover só a metade não usada
deixaria o kit capenga pra quem for usar `CardContent` amanhã, com custo de manter baixo.
Isso é diferente de `TIPO_OS_LABEL`/`VEHICLE_TYPE_LABEL`, que eram de fato artefato morto
de uma migração já concluída.

O padrão de fetch-on-mount (`refresh()` + `useEffect(refresh, [])`) repetido em ~9 páginas
foi **identificado mas não extraído pra um hook genérico** — cada página tem uma forma
levemente diferente (algumas buscam 2-3 recursos em paralelo, tipos de estado diferentes),
e um hook único arriscava reproduzir o mesmo tipo de bug do `t` nas dependências que acabou
de ser corrigido acima. Risco maior que o ganho pra este cleanup.

## Verificação (Fases 0–8)

- **Backend:** `./mvnw clean test` — build limpo, suíte completa passando após todas as
  mudanças de N+1, índices e `Lookups`.
- **Frontend:** `npm run typecheck` e `npm run lint` (zero warnings) após cada lote de
  edição, inclusive depois do cleanup.
- **Migration nova:** `V25__indices_manutencao_cnh_trip.sql` segue o padrão já
  estabelecido em `V8__indices_de_consulta.sql` (índice puro, sem mudança de schema).

## Fase 9: testes de `apps/web` + paginação (mesmo dia, continuação)

Retomado depois de uma revisão de status que apontou dois gaps: `apps/web`/`apps/mobile`
sem nenhum teste automatizado, e a paginação sinalizada como pendência arquitetural na
Fase 8 — desta vez com escopo e autorização explícitos do usuário pra mexer no contrato
de API.

### Testes (`apps/web`)

**O que mudou:** Vitest + React Testing Library + `@testing-library/user-event` instalados
(`apps/web` não tinha nenhum runner de teste; só lint/typecheck/build rodavam no CI).
`vite.config.ts` ganhou bloco `test` (jsdom, setup em `src/test/setup.ts`).
`tsconfig.json` ganhou os tipos `vitest/globals`/`@testing-library/jest-dom`. Novo script
`"test": "vitest run"` no `package.json`, adicionado como step no job `web` do CI
(`.github/workflows/ci.yml`), entre `typecheck` e `build:web`.

**Cobertura inicial (19 testes, 2 arquivos):**
- `src/lib/format.test.ts` — todas as funções puras de formatação (`formatBRL`,
  `formatDateBR`, `diasAteVencer`, `monthLabel`, `iniciais`, etc.), incluindo casos de
  borda (zero, janeiro/dezembro, nome único vs. composto).
- `src/lib/confirm.test.tsx` — teste de integração (não mock) do fluxo
  `deleteWithConfirm` + `<ConfirmDialogHost/>` (Radix `AlertDialog` renderizado de
  verdade em jsdom): confirmar chama `remove`/`onSuccess`/toast de sucesso; cancelar não
  chama nada; erro no `remove` mostra a mensagem certa (`Error.message` ou fallback).

**Bug de teste encontrado e corrigido durante a escrita:** os primeiros testes de
`diasAteVencer` usavam `vi.setSystemTime(Date.UTC(...))` à meia-noite UTC — em qualquer
fuso atrás de UTC (ex. `America/Sao_Paulo`), isso cai do lado errado da virada do dia
local, porque a função lê ano/mês/dia via `new Date().getFullYear()/getMonth()/getDate()`
(fuso local, de propósito — é assim que a função evita o mesmo tipo de bug em produção).
Corrigido fixando meio-dia local em vez de meia-noite UTC no teste.

**Escopo deliberadamente menor que "cobertura total":** não foram escritos testes de
página/hook nesta rodada — o objetivo era destravar a infraestrutura e cobrir os módulos
mais reaproveitáveis (formatters, o novo `deleteWithConfirm`) primeiro. `apps/mobile`
continua sem teste automatizado; fica como próximo item se/quando for priorizado.

### Paginação

Dos endpoints identificados na Fase 8 (`WorkOrders`, `Expenses`, `Chat.listMessages`,
`Trips`, `RoutePlans`, `Drivers`, `DriverRatings`), foram paginados os dois de maior
volume de crescimento real — viagens (telemetria diária por motorista) e ordens de
serviço (histórico plurianual do tenant inteiro). `RoutePlans`/`Drivers`/`DriverRatings`
ficam de fora por ora: crescem no ritmo do tamanho da frota, não do tempo, então a
cardinalidade é naturalmente pequena — reavaliar se isso deixar de ser verdade.

| Endpoint | Mudança |
|---|---|
| `GET /v1/trips` | `TripRepository`/`TripService`/`TripController` convertidos pra `Page`/`Pageable`, resposta agora `PageResponse<TripResponse>` (`page`/`size`, default 20, teto 100) |
| `GET /v1/work-orders` | Mesma conversão; `WorkOrderRepository` ganhou uma segunda query paginada (a versão sem paginação continua existindo, usada só por `maintenanceSummary`, que precisa do histórico completo de 12 meses pra montar o relatório) |
| `GET /v1/me/trips`, `GET /v1/me/vehicle/work-orders` | Contrato **não mudou** (continuam devolvendo lista simples) — por baixo, agora chamam a versão paginada do service com uma página fixa (20 e 50 itens respectivamente), já que essas telas (`DriverMorePage.tsx`) só mostram um resumo recente, nunca o histórico completo |

**Bug real pré-existente encontrado durante o trabalho:** `apps/mobile/src/api/client.ts`
tipava `vehicles.list()` como `VehicleResponse[]`, mas `GET /v1/vehicles` já era paginado
no backend há tempo (`PageResponse<VehicleResponse>`) — o app mobile nunca tinha sido
atualizado. `TripScreen.tsx` fazia `vehicles.map(...)` sobre o que na verdade era um
objeto `{content, page, ...}`, não um array. Provavelmente nunca creditado a esse bug
porque o teste manual costuma rodar com poucos veículos e o parser de rede local pode
mascarar o formato — mas quebraria em runtime assim que exercitado com dado real.
Corrigido junto (`PageResponse<T>` adicionado ao client mobile, `vehicles.list()` e
`trips.list()` agora tipados corretamente, `TripScreen.tsx` ajustada pra ler
`.content`).

**Por que Trips paginado é seguro pro caso de uso do mobile:** `TripScreen.tsx` usa
`trips.list()` pra achar a viagem em andamento (`trips.find(t => t.status ===
'EM_ANDAMENTO')`). Como a query ordena por `started_at desc` e uma viagem em andamento
tem, por definição, o `started_at` mais recente entre as viagens do motorista, ela nunca
pode "cair" pra uma página seguinte — está sempre entre os primeiros itens da página 0.

### Verificação (Fase 9)

- **Backend:** `./mvnw clean test-compile` (pegou 2 sites de mock desatualizados em
  `MeServiceTest`, corrigidos) e `./mvnw clean test` — suíte completa passando.
- **Frontend:** `npm run typecheck` (web e mobile), `npm run lint` e `npm run test` (novo)
  em `apps/web`, `npm run build` — todos limpos.

### Próximo passo combinado com o usuário

Arquitetura da Fase 2 (prontidão viária — pipeline de map matching, agregação de
`road_readiness_score`, import de extrato OSM, política de retenção aplicada) fica pra
uma sessão em **Opus**, a pedido do usuário — decisão de modelagem grande o bastante pra
justificar a troca (mesmo critério já registrado em memória: decisão de arquitetura
delicada → Opus antes de planejar). Este relatório e os specs/ADRs existentes
(`specs/02-dados-mapas-rotas.md`, `specs/05-roadmap-fases.md`, ADR 0009) são o ponto de
partida.
