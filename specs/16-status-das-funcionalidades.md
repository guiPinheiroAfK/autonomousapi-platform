# 16 — Status das Funcionalidades

Panorama do que existe no produto hoje, o que está pela metade e o que ainda não começou —
pra responder "isso já tem?" sem precisar ler spec por spec. Cada linha aponta pro spec/ADR
com o detalhe completo; este documento é o resumo, não a fonte de verdade (a fonte de
verdade continua sendo `specs/00` a `specs/15` + `docs/adr/`).

**Legenda:** ✅ implementado e no ar · 🚧 parcialmente implementado (parte pronta, parte
não) · 📋 planejado, ainda não começou.

Este documento reflete o estado em **2026-09-01**. Definition of Done marcado como `[ ]`
dentro de um spec individual nem sempre significa "não implementado" — alguns specs
antigos (09, 10) têm checklist desatualizado apesar do código já estar no ar; este
documento foi conferido contra o código real (`services/core-api/src/main/java`), não só
contra o texto dos specs.

## Conta e acesso

- ✅ **Cadastro e login manual**, com confirmação de e-mail obrigatória antes do primeiro
  login (ADR 0011).
- ✅ **Login/cadastro com Google** — implementado, mas **inativo em produção** até o
  `GOOGLE_CLIENT_ID` ser configurado no Cloud Console + VM + Netlify (spec 08, item 13).
- ✅ **Sessão persistente** — access token de 15min + refresh token de 30 dias rotacionado,
  retry automático em 401 antes de deslogar de verdade (spec 08, item 13).
- ✅ **Recuperação de senha** por e-mail (spec 08, item 11 — funcional, mas transacional só
  entrega de verdade pro próprio dono da conta até o domínio do Resend ser verificado).
- ✅ **Trial e bloqueio por assinatura** — `SubscriptionGate` (ADR 0010).

## Equipe e permissões

- ✅ **Papéis por tenant** — `GESTOR_FROTA` (dono), `DESPACHANTE` (cria/atribui rota, não
  cancela nem mexe fora de rota), `VISUALIZADOR` (só leitura) — spec 15.
- ✅ **Convite de equipe** por e-mail (token, aceite define senha) — mesmo padrão do convite
  de motorista (ADR 0013).
- 📋 **Chat entre membros da equipe** (Gestor↔Despachante, etc.) — hoje o chat só modela
  Gestor↔Motorista (ADR 0015/0022); expandir pra equipe é ideia registrada, não é spec
  ainda.

## Frota (veículos)

- ✅ **CRUD de veículo**, tela própria `/frota/:id` (spec 08, item 1), ícone por tipo (item
  4), máscara de digitação em campos numéricos (item 16).
- ✅ **Condição do veículo** — sinistro (`VehicleIncident`) e valor de mercado FIPE
  (`VehicleMarketValue`), lançamento manual — consulta automática de FIPE fica de fora por
  ora (o matching marca/modelo→código de catálogo da API pública é problema à parte, spec
  06).
- ✅ **Manutenção por modelo — decisão consciente de manter manual** (ADR 0017), sem
  integração com fabricante (não existe API pública confiável no Brasil pra isso).

## Motoristas

- ✅ **CRUD de motorista**, convite de acesso ao app (ADR 0013), designação de veículo (ADR
  0014).
- ✅ **Avaliação manual de motorista** (nota do gestor) — `DriverRatingManual`, autorização
  testada ponta a ponta (spec 06).
- 🚧 **Avaliação automática** — existe `DriverRatingAuto`/`DriverAutoRatingJob` no código,
  mas o spec 06 marca essa peça como dependente do pipeline de GPS maduro (Fase 3); vale
  conferir a maturidade real antes de anunciar como pronta.
- ✅ **Consentimento de avaliação de desempenho** no fluxo de onboarding do motorista —
  `LocationConsentScreen.tsx` (PR #50); DoD do spec 06 estava desatualizado, item já
  implementado.

## Ordens de serviço e manutenção

- ✅ **CRUD de ordem de serviço**, itens de serviço com máscara de valor em R$ (spec 08, item
  16), alerta de manutenção agendada via notificação in-app (`MANUTENCAO_AGENDADA`).

## Rotas, coleta e entrega

- ✅ **Cadastro de rota multi-parada** (`ROTA`) e transfer ponto-a-ponto (`TRANSFER`), com
  sugestão de ordem via OSRM (ADR 0018).
- ✅ **Pontos de coleta reutilizáveis** — geocodificação via Nominatim, sem lib de mapa (spec
  08, item 5).
- ✅ **Passageiro/contato de parada** — cadastro inline dentro do fluxo de criar rota (não é
  tela própria, decisão de UX deliberada), reaproveitável entre rotas (spec 14, parte 1).
- ✅ **Viagem redonda (ida e volta vinculadas)** — `viagem_id` compartilhado, indicador na
  lista, rentabilidade combinada (spec 13).
- ✅ **Cancelamento, reatribuição e solicitação do motorista** — cancelamento direto
  (`PLANEJADA`) ou via chat (`EM_ANDAMENTO`); motorista solicita, gestor decide (ADR 0021).
- ✅ **Telemetria do próprio fluxo** — `route_plan_event` gravado nas transições (criada,
  atribuída, parada concluída, concluída, cancelada), sem painel consumindo ainda (ADR
  0020).
- ✅ **App mobile mostra a rota atribuída** e permite concluir parada (`MinhaRotaScreen.tsx`,
  spec 11).
- ✅ **Notificação automática ao passageiro** (Telegram) — confirmação, "a caminho",
  embarque confirmado e cancelamento disparam sozinhos nas transições que
  `RoutePlanService` já grava; botão "Avisar passageiro" no app do motorista pra disparo
  manual. Vínculo por deep-link (`t.me/<bot>?start=<token>`, o passageiro clica e dá
  `/start` uma vez) — Telegram só deixa bot mandar mensagem pra quem já iniciou conversa
  com ele antes (spec 14, achado na implementação). Testado ponta a ponta contra o
  backend real (webhook simulado, 5 gatilhos confirmados via log). **Ativação em produção
  pendente** de criar o bot (`@BotFather`) e configurar `TELEGRAM_BOT_TOKEN`/
  `TELEGRAM_BOT_USERNAME`/`TELEGRAM_WEBHOOK_SECRET` — mesmo padrão do Google OAuth (spec
  08, item 13).
- ✅ **Gaps priorizados do levantamento de rota** — push consistente entre os dois caminhos
  de atribuição, gestor acompanhar progresso em tempo real (painel com poll), fallback do
  OSRM visível pro gestor, edição de rota já `PLANEJADA` (`PUT /v1/routes/plans/{id}`) —
  spec 11, DoD, último item.

## Chat (mensagens gestor↔motorista)

- ✅ **Mini-chat 1:1**, retenção híbrida (servidor guarda só janela curta, histórico
  completo no dispositivo do gestor) — ADR 0015.
- ✅ **Anexar/cancelar/trocar rota pela conversa**, mensagem estruturada (ADR 0021).
- ✅ **Responder, editar, excluir, encaminhar, reagir** — editar até 20min depois de
  enviada, excluir até 35min; reação com paleta fixa de 6 emojis (ADR 0022).
- ✅ **Layout mobile de uma tela por vez** (lista OU conversa, com slide), coluna de
  mensagens centralizada em tela larga.
- 📋 **Chat entre membros da equipe** — ver seção "Equipe e permissões" acima.
- 📋 **Anexo de mídia** — texto puro por design; fica pra quando (se) virar pedido real (ADR
  0015, "reavaliar quando").

## Custos e orçamento

- ✅ **Despesas categorizadas** (`expense_entry`), separadas do "lançamento genérico"
  anterior (spec 10 — código no ar, DoD do spec desatualizado).
- ✅ **Orçamento com alerta de estouro** (80%/100%, por veículo ou frota) — `Budget`,
  `BudgetAlertJob`.
- ✅ **Custo estimado por rota** (`RouteCostEstimator`) e valor sugerido com margem — spec 09
  (código no ar, DoD do spec desatualizado).
- ✅ **Rentabilidade de transfer** — margem realizada, soma por viagem redonda quando
  aplicável (spec 10 + spec 13).
- 🚧 **Recalibração automática (estimado × realizado)** — o job de comparação mensal existe
  no desenho da spec 09/10; confirmar se está rodando de fato antes de marcar como 100%
  fechado.

## Notificações

- ✅ **Notificações in-app** — sino no topbar com contador real, tipo (`ORCAMENTO_ALERTA`,
  `CNH_VENCENDO`, `MANUTENCAO_AGENDADA`, `AVISO_GESTOR`), lida/não lida, tela "ver todas"
  (spec 08, item 12).
- ✅ **Push** — `PushNotificationService`, usado por chat, alertas de orçamento/CNH/manutenção
  (ADR 0016).
- ✅ **Notificação operacional interna** — Discord "Incoming Webhook" avisa o time no
  signup e na confirmação de e-mail (`NotificationWebhookSender`/
  `DiscordNotificationWebhookSender`, spec 12). Uso interno, não é feature de cliente
  final. **Ativação em produção pendente** de criar o webhook no canal do Discord e
  configurar `OPERATIONAL_WEBHOOK_URL` — mesmo padrão do Google OAuth/Telegram (spec 08,
  item 13).
- ✅ **Notificação automática ao passageiro** — ver seção "Rotas, coleta e entrega" acima.

## Pontos de recarga elétrica

- 🚧 **Listagem de pontos de recarga** — `ChargingStationController` existe, mas depende de
  provedor externo de dado; sem provedor real identificado ainda, sem fallback gracioso
  implementado pra quando o provedor está fora do ar (spec 06, DoD).

## Relatórios e parceiros

- ✅ **Relatórios** — tela própria com dados agregados de custo/frota (`ReportsPage.tsx`).
- ✅ **Catálogo de afiliados** — estrutura pronta pra qualquer volume de parceiro, mas só 1
  parceiro de exemplo cadastrado hoje (cadastro é manual, direto no banco — não compensa
  automatizar antes de ter volume, spec 08 item 3).

## Assinatura (billing)

- ✅ **Trial + assinatura via Stripe** — checkout, portal do cliente, webhook de status
  (`BillingService`, ADR 0010).

## Prontidão viária (subproduto de dado) — Fase 2 em diante

- 📋 **Pipeline de map matching** (ping de GPS → `road_segment_observation`) — ainda não
  começou, é o objetivo central da Fase 2 (spec 05).
- 📋 **`road_readiness_score`** agregado por trecho — depende do item acima.
- 📋 **API pública de prontidão viária** (`/public/v1/road-readiness/...`) — Fase 4, licenciar
  pra parceiro de AV.

## App mobile do motorista

- ✅ **App redesenhado em torno de "operador, não gestor"** — tela "Hoje" como home, CNH, OS
  read-only, ocorrência, histórico de viagem, chat (spec 07).
- ✅ **Rastreamento de GPS sobrevive à troca de aba** — hook levantado pra `HomeTabs`, achado
  e corrigido nesta sessão (bug de perda de dado, não só performance).
- ✅ **Refresh de sessão silencioso** — o app salvava o refresh token no SecureStore desde o
  início, mas nunca o usava (achado nesta sessão numa segunda revisão); access token
  expirado (15min) jogava pro login de novo mesmo com refresh token de 30 dias ainda
  válido guardado à toa. Corrigido espelhando o client web: `request()` tenta renovar a
  sessão sozinho num 401 antes de desistir.
- 📋 **Ações novas do chat (responder/editar/excluir/encaminhar/reagir) só no navegador** —
  o app nativo (`apps/mobile`) ainda não ganhou essas ações (ADR 0022, "fora de escopo").

## Infra, qualidade e segurança

- ✅ **CI/CD, branch protection em `main` e `develop`** (spec 08, itens 9).
- ✅ **`npm audit` do `apps/web` zerado** (spec 08, item 7).
- ✅ **Code-splitting por rota**, bundle principal caiu de ~705KB pra ~383KB (Vite 5→8, spec
  08, item 8).
- ✅ **Otimizações de fetch percebido** — cache com TTL por natureza do dado, dedupe,
  prefetch no hover, guarda contra resposta obsoleta, update otimista (spec 08, item 14).
- 📋 **`npm audit` da raiz (workspace)** — 27 alertas, todos em toolchain de build do mobile
  (Expo/Metro), não expostos em produção; resolver exige upgrade de major do Expo SDK,
  sessão própria com teste manual (spec 08, item 15).
- 📋 **Domínio verificado no Resend** — e-mail transacional só entrega de verdade pro dono da
  conta até isso ser feito (spec 08, item 11).

## Próximas fases (visão de produto, não checklist de sprint)

Ver `specs/05-roadmap-fases.md` para o desenho completo. Resumo:

- **Fase 2** — pipeline de prontidão viária começa a existir de verdade (map matching, score
  agregado, roteamento básico exposto).
- **Fase 3** — piloto "Vila Inteligente": roteamento passa a pesar pelo score, primeira
  validação de visão computacional, avaliação automática de motorista madura, condição do
  veículo com dado real acumulado.
- **Fase 4** — parceiro de AV: API pública de prontidão viária, SLA formal, contrato de
  dados/LGPD.
