# QA Report — sessão de 2026-08-12

Duas rodadas: a primeira autônoma (features da Fase 1, em Sonnet 5) e a segunda de
engenharia — auditoria de performance, testes de integração e Redis, em Opus 5.

## Resumo por PR

| # | Título | Status | Branch |
|---|---|---|---|
| [#19](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/19) | Clone visual do FrotaOS: Ordens de Serviço, Manutenção, Relatórios | ✅ mesclada em `develop` | `feature/web-clone-frotaos` |
| [#20](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/20) | Export de relatório em CSV | ✅ mesclada em `develop` | `feature/export-relatorio-csv` |
| [#21](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/21) | Billing web-first via Stripe Checkout | ✅ mesclada em `develop` | `feature/billing-stripe-web-first` |
| [#22](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/22) | Registro de viagem do motorista: backend | ✅ mesclada em `develop` | `feature/mobile-trip-logging-backend` |
| [#23](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/23) | Registro de viagem do motorista: mobile | ✅ mesclada em `develop` | `feature/mobile-trip-logging` |
| [#25](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/25) | Performance: N+1, índices e pings em lote | ✅ mesclada em `develop` | `feature/perf-indices-e-n1` |
| [#26](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/26) | Testes de integração contra Postgres real | ✅ mesclada em `develop` | `test/integracao-testcontainers` |
| [#27](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/27) | Redis: rate limit no login e cache | ✅ mesclada em `develop` | `feature/redis-rate-limit-e-cache` |
| [#28](https://github.com/guiPinheiroAfK/autonomousapi-platform/pull/28) | Code splitting, correção do cache e limpeza | ✅ mesclada em `develop` | `chore/bundle-limpeza` |

## 1. Clone do FrotaOS (front)

**O que mudou:** Sidebar/Topbar clonados (seções Operação/Gestão, busca, seletor de unidade mock, sino de notificações). Frota e Motoristas ganharam filtro de busca/status. Frota ganhou dialog de detalhe com abas Manutenção/Ordens de Serviço. Três páginas novas: Ordens de Serviço (mock), Manutenção (dado real de custo categoria `MANUTENCAO`), Relatórios (mock).

**Por que mock em 3 páginas:** Ordens de Serviço e Relatórios não têm entidade correspondente no backend ainda — a pedido explícito, ficaram mockadas no front (dado próprio, usando as placas/motoristas reais do tenant demo RotaCerta) "pra usar no futuro" quando o backend existir.

**Verificação:**
- `npm run typecheck`/`lint`/`build` (workspace web) — limpos
- Navegador: todas as 7 telas testadas manualmente (Dashboard, Frota, Ordens de Serviço, Motoristas, Manutenção, Relatórios, dialog de detalhe de veículo) com dado real/demo, sem erro de console persistente

## 2. Export de relatório (CSV)

**O que mudou:** `GET /v1/reports/costs.csv` — CSV tenant-scoped de todos os lançamentos de custo da frota (placa, marca, modelo, categoria, descrição, data, valor). Botão "Exportar relatório" no Relatórios dispara o download.

**Bug encontrado e corrigido:** `LocalDate.MIN` como parâmetro de query estourava o range de `date` do Postgres (`169087565-03-15` > máximo suportado), causando erro 500 mascarado como 401 no filtro de segurança. Corrigido usando uma data fixa (`2000-01-01`) em vez do mínimo teórico do Java.

**Verificação:**
- `./mvnw test` — 24 testes (1 novo, cobre cabeçalho + escaping de `;`/aspas no CSV)
- `curl` contra Postgres real com demo seed — CSV correto, 65 lançamentos
- Isolamento por tenant confirmado (tenant novo/vazio recebe só o cabeçalho)
- Download verificado no navegador (`GET /api/v1/reports/costs.csv` → 200)

## 3. Billing web-first (Stripe)

**O que mudou:** Modelo `tenant -> subscription`, com `billing_source` (`WEB_STRIPE`/`IOS_IAP`/`ANDROID_IAP`) modelado para múltiplos canais desde já, mesmo só o primeiro implementado — conforme spec 03. Três endpoints: `GET /v1/billing/subscription`, `POST /v1/billing/checkout` (Stripe Checkout hospedado, quantidade = veículos ativos), `POST /v1/billing/webhook` (verificação de assinatura HMAC, rota pública pois é a própria Stripe chamando).

**Decisão de arquitetura (documentada no spec 03):** cobrança via web, fora do app mobile — evita ficar preso às políticas de IAP (até 30% de comissão) da Apple/Google. App mobile continua ferramenta operacional pra quem já assina.

**⚠️ Pendente — precisa de você:** Não crio contas em serviços externos (Stripe incluso) nem manipulo credenciais reais — isso é regra minha, não brecha de escopo. O código está pronto e testado no "modo não configurado": sem `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`/`STRIPE_PRICE_ID`, o checkout responde 503 com mensagem clara em vez de crashar. **Para testar um checkout de verdade** (ponta a ponta, em modo teste da Stripe): crie uma conta Stripe (ou use uma existente), pegue as chaves de teste e o price ID de um produto recorrente, e me passe as três variáveis de ambiente — eu subo o checkout real a partir daí.

**Verificação:**
- `./mvnw test` — 28 testes (4 novos: sem assinatura, com assinatura, checkout sem chave → erro claro, webhook sem segredo → erro claro)
- Migration V6 aplicada em Postgres real, confirmada via log do Flyway
- `curl`: subscription sem assinatura → `hasSubscription:false`; checkout sem chave → 503; webhook sem JWT → rota pública alcançável (503 por falta de secret, não 401 — confirma que a rota pública está corretamente liberada)
- Navegador: página Assinatura renderiza; botão "Assinar agora" mostra o erro 503 esperado

## 4. Registro de viagem do motorista (backend + mobile)

**Backend (core-api):** sessão de viagem (início/fim) no schema `core`; o ping bruto de GPS é encaminhado pro geo-api (schema `geo`) — core-api nunca guarda o ping duas vezes, só orquestra (spec 01: mobile nunca fala com geo-api direto). `POST /v1/trips` (inicia, valida veículo do tenant, rejeita segunda viagem simultânea com 409), `POST /v1/trips/{id}/stop`, `GET /v1/trips`, `POST /v1/trips/{id}/pings`. Só role `MOTORISTA` acessa.

**Mobile:** fluxo real ponta a ponta — escolher veículo → iniciar viagem → capturar GPS de verdade (`expo-location`, `watchPositionAsync`) → enfileirar localmente (AsyncStorage, sobrevive a reinício do app) → sincronizar (agora envia de verdade pro backend) → finalizar. Sessão persistida em `expo-secure-store` (não pede login de novo ao reabrir). Tela de consentimento de localização própria no onboarding. Tela de viagem só aparece pra conta `MOTORISTA` (checado via `/v1/auth/me`); outras roles veem uma tela de bloqueio.

**Bug de ambiente encontrado e corrigido:** `expo@51` puxa transitivamente `@expo/vector-icons@14.1.0`, que instala um `react-native@0.87.0` duplicado e incompatível dentro do próprio `node_modules/expo` — o Metro (bundler do React Native) resolvia essa cópia errada e falhava ao empacotar o app inteiro. `npm overrides` sozinho não resolve (testado — a duplicata reaparecia mesmo com o override, provavelmente uma limitação do npm com overrides em várias camadas de dependência transitiva). Corrigido com um script `postinstall` (`scripts/fix-mobile-duplicate-react-native.js`) que remove o diretório duplicado sempre que reaparecer — testado e confirmado durável após reinstall limpo do zero.

**⚠️ Limite desta verificação:** este ambiente não tem device físico, emulador Android/iOS nem a toolchain nativa (Android Studio/Xcode) — não dá pra abrir o app de verdade numa tela. A verificação foi: (1) `tsc --noEmit` limpo; (2) `npx expo export` — o Metro empacota o app inteiro (500+ módulos) sem nenhum erro de import/sintaxe, o único erro que sobra é a ausência do compilador nativo `hermesc` (não existe nesse container, não é bug de código, não afeta o fluxo via Expo Go); (3) o backend de viagem foi testado de ponta a ponta de verdade via `curl` simulando exatamente as chamadas que o app faz (login motorista real → iniciar viagem → ping → **confirmei o ping persistido em `geo.vehicle_gps_ping` direto no Postgres** → finalizar → listar). Ou seja: o código roda e o backend funciona de verdade; o que não pôde ser verificado é a experiência visual/tátil no aparelho (permissões do sistema operacional aparecendo, GPS real do hardware, etc.) — vale um teste seu no Expo Go quando puder.

## O que ficou pendente da Fase 1 completa

- App mobile publicável (build interno TestFlight/Play Internal Testing) — falta `bundleIdentifier`/`package` e não foi testado em device real (ver limite acima)
- Localização em background de verdade (hoje captura em foreground; rastreio contínuo com app minimizado precisa de `expo-task-manager`, não testável sem device físico)
- Fluxo de convite de motorista (hoje só existe cadastro público como `GESTOR_FROTA` — criei um usuário `MOTORISTA` direto no banco só pra testar o fluxo, e já removi depois)
- Schema `geo`: pipeline de ingestão de GPS já existe e funciona (endpoint interno confirmado), mas não há agregação/processamento ainda — isso é Fase 2 do spec, não Fase 1
- Teste ponta-a-ponta do billing com chave real da Stripe (ver seção 3)

## 5. Rodada de engenharia (PRs #25 a #28)

Auditoria do que já existia, sem feature nova.

**Performance (#25).** A tela de Manutenção fazia 1+N requisições HTTP — a lista de veículos
e depois os custos de cada veículo, 13 chamadas com 12 veículos e crescendo com a frota.
Novo `GET /v1/vehicles/costs` devolve tudo em uma query; medido no navegador: **13 → 2**
requisições, mesma tela. Três índices em caminhos que faziam sequential scan, com destaque
para `subscription.stripe_customer_id`, que o webhook da Stripe consulta a cada evento.
Envio de ping virou lote (um motorista voltando de uma hora sem sinal disparava ~240
chamadas sequenciais).

**Testes de integração (#26).** Postgres real na suíte. Foi a mudança de maior valor: teste
com repositório mockado não executa SQL, e por isso o bug do `LocalDate.MIN` passou por 24
testes verdes. Validei que o teste de regressão presta reintroduzindo o bug de propósito e
vendo a suíte ficar vermelha. O CI agora roda contra um Postgres de service container.

**Redis (#27).** O login não tinha limite algum de tentativas — buraco presente, não risco
futuro. Agora conta por e-mail e por IP. Cache de agregado do dashboard com chave por
tenant. Kafka ficou documentado no ADR 0006 com o gatilho exato para a Fase 2, em vez de
entrar sem necessidade.

**Bundle e limpeza (#28).** Bundle inicial de 751 KB → 272 KB: o recharts saiu do carregamento
inicial (antes até quem só abria a tela de login baixava a biblioteca de gráficos).

### Dois bugs meus que os testes pegaram — e um que quase escapou

1. **`CacheErrorHandler` não estava ligado.** Declarar o `@Bean` não basta: a classe precisa
   implementar `CachingConfigurer`. Ou seja, minha "degradação graciosa" era decorativa e o
   Redis fora derrubava o dashboard. O teste de integração (que roda sem Redis) pegou.
2. **Cache quebrado no HIT.** O valor voltava do Redis como `LinkedHashMap` em vez do DTO,
   porque nossos DTOs são `record` (classes final) e a tipagem padrão do serializer pula
   classes final ao gravar o tipo. **O miss funcionava; só o hit quebrava.**
3. **Como quase escapou:** minha verificação do cache mediu *tempo* (`curl -o /dev/null`) e
   nunca o *corpo*. O endpoint respondia rápido e errado. Quem pegou foi a verificação no
   navegador, e agora há teste travando a ida e volta da serialização.

Fica a lição para as próximas: medir latência não é verificar correção.

## Checklist da Fase 1 (specs/05-roadmap-fases.md) — status ao final desta sessão

| Item | Status |
|---|---|
| `core-api`: auth multi-perfil | ✅ |
| `core-api`: CRUD veículo/motorista | ✅ |
| `core-api`: custo por km | ✅ |
| `core-api`: alertas de manutenção/vencimento | ✅ |
| `web`: cadastro, dashboard, alertas | ✅ |
| `web`: export de relatório | ✅ (nesta sessão) |
| Billing (ao menos 1 canal) | ✅ código pronto, ⚠️ falta chave real de teste pra validar ponta a ponta |
| `mobile`: registro de viagem do motorista | ✅ (nesta sessão) — não testado em device físico |
| Schema `geo`: ingestão bruta de GPS | ✅ confirmado funcionando (endpoint interno testado de verdade) |
| Repositório e CI/CD | ✅ (de antes desta sessão) |

Sobra pra fechar a Fase 1 por completo: chave de teste da Stripe (você) e um teste em device/emulador real do app mobile (você, ou me diga se quer que eu tente configurar um emulador Android aqui — não tentei ainda porque normalmente precisa de virtualização que containers não costumam ter).

## Modelo usado

**Sonnet 5** nas PRs #19 a #23 (implementação de features já bem especificadas pelo spec).
A única decisão de escopo não-trivial ali foi a modelagem mínima do billing — sem
`subscription_item` por veículo, com a quantidade calculada no checkout —, documentada na
PR #21.

**Opus 5** nas PRs #25 a #28, onde o trabalho era de julgamento e não de execução: decidir
o que valia corrigir, e principalmente decidir o que **não** fazer. A recomendação de adiar
Kafka (ADR 0006) e a de usar Redis apenas onde ele se paga (ADR 0007) foram as saídas mais
importantes dessa rodada — junto com achar dois bugs meus antes que virassem problema seu.
