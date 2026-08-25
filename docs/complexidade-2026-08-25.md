# Levantamento de arquivos/classes complexos — 2026-08-25

Relatório apenas — nenhum destes itens foi refatorado agora. Serve como lista de
priorização para quando o time decidir investir tempo em reorganização. Gerado por
auditoria de 4 agentes (um por subprojeto) em paralelo, cobrindo o monorepo inteiro.

Para o levantamento paralelo de código morto/duplicado (a limpeza mecânica feita
junto com este relatório), ver o commit correspondente — os únicos dois achados
seguros foram um `hojeISO()` duplicado em `apps/web` e um helper de teste duplicado
em `services/geo-api`, ambos já corrigidos. O resto da base (especialmente
`services/core-api` e `apps/mobile`) já estava limpo, sem achados de baixo risco.

## services/core-api

| Arquivo | Linhas (aprox.) | Problema | Sugestão futura |
|---|---|---|---|
| `core/routeplan/RoutePlanService.java` | 379 | Mistura algoritmo geométrico (`haversineKm`, `nearestNeighbor`, `otimizarGrupo`), validação e orquestração CRUD/DTO numa classe só. | Extrair a otimização geométrica pra uma classe de estratégia separada da orquestração de negócio. |
| `core/chat/ChatService.java` | 270 | Mensageria, sync multi-dispositivo, push e mapeamento de DTO tudo junto — "God service". | Separar mapper de resposta e isolar lógica de typing/sync-cursor em serviços auxiliares. |
| `core/auth/AuthService.java` | 253 | Signup, login, verificação de e-mail, reset de senha, refresh e aceite de convite numa classe. | Dividir por fluxo (`PasswordResetService`, `EmailVerificationService`), mantendo `AuthService` só para login/refresh. |
| `core/workorder/WorkOrderService.java` | 225 | CRUD de OS misturado com geração de relatório agregado de manutenção. | Extrair `MaintenanceReportService`. |
| `core/demo/DemoDataSeeder.java` | 220 | Grande, mas esperado pra um seeder — nota de tamanho, não urgência. | Se crescer mais, quebrar por agregado (veículos/motoristas/viagens). |
| `core/geo/GeoApiClient.java` | 216 | Um client HTTP cobre rotas, geocodificação e estações de recarga — 3 integrações externas distintas. | Dividir por capacidade (`RoutingClient`, `PlacesClient`, `ChargingStationsClient`) se a superfície continuar crescendo. |

## services/geo-api

| Arquivo | Linhas (aprox.) | Problema | Sugestão futura |
|---|---|---|---|
| `app/routers/internal.py` | 448 | Maior arquivo do serviço — 8 domínios não relacionados (GPS, road readiness v1/v2, sessionização, quality metrics, charging, driving events, roteamento) num único router, com schemas Pydantic inline. | Dividir em routers por domínio (`routers/gps.py`, `routers/road_readiness.py`, `routers/charging.py`, etc.) com schemas em módulos próprios. |
| `app/models.py` | 233 (7 classes) | Mistura 3 domínios sem relação direta (GPS/road readiness, sessionização, charging) num arquivo só. | Separar em `models/geo.py` e `models/charging.py`, com `models/__init__.py` reexportando pra não quebrar imports. |
| `app/routing.py` | 204 | Dataclasses de domínio + cliente HTTP + parsing/tradução de erro tudo junto. | Separar client HTTP de tradução de resposta se crescer (cache, retry). |
| `app/road_readiness_v2.py` | 212 | Função `recalcular_road_readiness_v2` com ~90 linhas fazendo leitura + agregação + cálculo + upsert junto. | Extrair o cálculo puro (já isolado em funções auxiliares) do orquestrador de I/O. |

## apps/web

| Arquivo | Linhas (aprox.) | Problema | Sugestão futura |
|---|---|---|---|
| `pages/LandingPage.tsx` | 828 | Página de marketing inteira num componente só, dezenas de seções inline. | Quebrar por seção (`Hero`, `FeatureGrid`, `PricingSection`, `Footer`) em `pages/landing/`. |
| `pages/CostsPage.tsx` | 809 | 3 sub-páginas num arquivo (resumo/gráfico, despesas paginadas + modal, orçamentos). | Dividir em `CostsOverviewTab`, `CostsEntriesTab`, `BudgetsTab`. |
| `pages/DriversPage.tsx` | 628 | Lista/CRUD de motorista + modal de detalhe com avaliação, designação de veículo e convite — 3 domínios de estado num componente. | Extrair `DriverDetailModal` com seus sub-blocos como filhos. |
| `pages/WorkOrdersPage.tsx` | 618 | Lista+filtro, modal de criação/edição e drawer de detalhe tudo inline. | Separar `WorkOrderFormModal` e `WorkOrderDetailDrawer`. |
| `pages/ChatPage.tsx` | 491 | Fetch/polling, IndexedDB local, picker de motorista, picker de rota e UI de bolhas — 8 `useEffect` encadeados. | Extrair `ConversationList`, `MessageThread`, `AttachRoutePicker`, mover polling pra um hook dedicado. |
| `pages/VehiclesPage.tsx` | 441 | Lista+filtros+modal de criação/edição do veículo. | Extrair `VehicleFormModal` e `VehiclesTable`. |
| `pages/RoutePlansPage.tsx` | 455 | Formulário de criação de rota embutido junto com listagem/paginação. | Separar `RoutePlanForm` de `RoutePlanList`. |
| `App.tsx` | 405 | Guards de rota + árvore de rotas + wrappers de parâmetro no mesmo arquivo. | Mover guards para `auth/guards.tsx`. |

## apps/mobile

| Arquivo | Linhas (aprox.) | Problema | Sugestão futura |
|---|---|---|---|
| `src/screens/TripScreen.tsx` | 197 | Fetch, permissão + watch de GPS, fila offline, chamadas de start/stop/sync e UI tudo num componente. | Extrair hooks `useTripLocationTracking()` e `useTripSync()`. |
| `src/api/client.ts` | 268 | ~20 interfaces de domínios distintos (auth, veículos, viagens, chat, recarga, rotas, incidentes) + todos os endpoints num arquivo só. | Separar tipos por domínio (`api/types/*.ts`) ou por tela. |
| `src/screens/HomeScreen.tsx` | 219 | Card de perfil/CNH + card de veículo/OS + modal completo de "reportar ocorrência" com form próprio. | Extrair `<ReportIncidentModal>` como componente próprio. |
| `src/screens/RouteScreen.tsx` | 238 | Formatação/tradução de manobras OSRM + tela de rota + subcomponente de autocomplete de endereço, tudo junto. | Mover formatadores pra `src/routes/formatRoute.ts`, `BuscaEndereco` pra `src/components/`. |

## Achado avulso (não é tamanho, é decisão pendente)

`apps/mobile/src/auth/tokenStorage.ts` grava `refreshToken` no SecureStore, mas
nada no app lê ou usa esse valor pra renovar sessão — é uma feature pela metade
(falta implementar o fluxo de refresh, ou remover a gravação). Não é limpeza
mecânica; precisa de decisão de produto sobre se o refresh automático entra no
roadmap do app do motorista.
