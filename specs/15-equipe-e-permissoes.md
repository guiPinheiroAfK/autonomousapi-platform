# 15 — Equipe e Permissões (papéis restritos por tenant)

## Contexto

Hoje só existe **um** usuário de gestão por tenant — quem se cadastrou (`GESTOR_FROTA`).
Não existe convite pra um segundo gestor/despachante entrar no mesmo tenant (só existe
convite de motorista, ADR 0013). Pedido do Guilherme: mais de uma pessoa vai acessar a
mesma operação, e quem entra depois não deveria ter controle total por padrão — só quem
já é dono da conta decide o que cada novo integrante pode fazer.

**Decisão de escopo (confirmada):** papel fixo por pessoa, valendo pra toda a operação do
tenant — não é permissão por rota individual. Convite de equipe usa o mesmo padrão do
convite de motorista (e-mail com token, aceite define senha).

## Papéis novos

- **`GESTOR_FROTA`** (existente, sem mudança de comportamento) — dono da conta. Único papel
  que pode convidar/gerenciar equipe e mexer em Assinatura/Billing.
- **`DESPACHANTE`** (novo) — lê tudo que o Gestor lê (exceto Assinatura), mas só escreve em
  rotas: criar rota, sugerir ordem, atribuir motorista. Não cancela rota, não cria/edita/
  exclui nada fora de rotas (veículo, motorista, custo, OS, etc.).
- **`VISUALIZADOR`** (novo) — só leitura, em tudo exceto Assinatura. Nenhuma escrita em
  lugar nenhum.

Papel padrão de quem aceita um convite novo: **`VISUALIZADOR`** — o Gestor eleva pra
Despachante depois, se quiser (pedido explícito: "ele vai ser sempre apenas alguém que
pode visualizar, só quem [é dono] pode dar permissão").

## Por que isso é mais simples do que parece

Auditoria do backend (2026-08-31): a maioria dos endpoints de **leitura** (Frota,
Motoristas, Custos, OS, etc.) não tem `@PreAuthorize` nenhum — a regra padrão do
`SecurityConfig` é "qualquer usuário autenticado do tenant passa", a tela é quem esconde o
menu. Ou seja, `VISUALIZADOR`/`DESPACHANTE` **já leem tudo por padrão**, sem precisar tocar
em nada — só preciso: (1) fechar esse acesso explicitamente em Assinatura/Billing (decisão
confirmada: esconder dos dois papéis novos), e (2) abrir explicitamente as 3 escritas de
rota pro `DESPACHANTE` nos poucos endpoints que já são `@PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")`.

## Modelo de dados

- Nenhuma tabela nova pro papel em si — `app_user.role` já é uma coluna de texto livre
  (enum Java), só ganha dois valores novos.
- `team_invite` (schema `core`) — mesmo desenho de `driver_invite` (token hash SHA-256,
  TTL, `used_at`), mas sem vínculo com um registro operacional pré-existente (motorista já
  existe antes do convite; um novo membro de equipe não): `email`, `nome`, `role`
  (`VISUALIZADOR` ou `DESPACHANTE` — nunca `GESTOR_FROTA`/`ADMIN` por esse caminho, evita
  escalonamento de privilégio via convite), `invited_by_user_id`, `token_hash`,
  `expires_at`, `used_at`.

## Endpoints novos

- `POST /v1/team/invite` (Gestor-only) — e-mail, nome, papel. Bloqueia se e-mail já é login
  de alguém.
- `GET /v1/team` (Gestor-only) — lista `GESTOR_FROTA`/`DESPACHANTE`/`VISUALIZADOR` do tenant
  com papel atual (mais convites pendentes).
- `PUT /v1/team/{userId}/role` (Gestor-only) — muda o papel de um integrante existente
  (nunca para si mesmo, nunca para `GESTOR_FROTA`).
- `DELETE /v1/team/{userId}` (Gestor-only) — remove o acesso (desabilita o login).
- `POST /v1/auth/accept-team-invite` (público, token prova posse) — cria o `app_user` no
  papel definido no convite.

## Mudança nos endpoints existentes

- `RoutePlanController`: `suggest-order`, `create` (`POST /v1/routes/plans`), `assign`
  passam a aceitar `DESPACHANTE` além de `GESTOR_FROTA`/`ADMIN`. `cancel` e `list`
  continuam só `GESTOR_FROTA`/`ADMIN` — espera, `list` (leitura) deveria abrir pros três
  papéis (é como o Despachante vê a lista de rotas pra poder atribuir). Corrigido: `list`
  abre pros três papéis de gestão; `cancel` continua fechado.
- `BillingController`: `GET /v1/billing/subscription` ganha `@PreAuthorize` explícito
  (`GESTOR_FROTA`/`ADMIN`) — hoje está sem nenhum, achado da auditoria, fecha de graça
  junto dessa mudança.

## Front-end

- `RequireGestor` (guard de rota) passa a aceitar os três papéis de gestão, não só
  `GESTOR_FROTA` — senão `DESPACHANTE`/`VISUALIZADOR` são redirecionados pra fora do painel
  inteiro.
- Item "Assinatura" do menu lateral só aparece pra `GESTOR_FROTA`.
- Tela nova "Equipe" (menu, Gestor-only) — convidar (e-mail, nome, papel), listar
  integrantes + convites pendentes, mudar papel, remover.
- Botão "Nova rota" e picker de motorista continuam visíveis pro Despachante; botão
  "Cancelar rota" só aparece pro Gestor.
- **Fora de escopo desta fatia (fast-follow):** esconder/desabilitar botão de criar/editar/
  excluir nas outras ~15 telas (Frota, Motoristas, Custos, OS, Manutenção, etc.) pro
  Despachante/Visualizador. O backend já bloqueia essas escritas pra quem não é
  `GESTOR_FROTA`/`ADMIN` (comportamento já existente, sem mudança) — o buraco que sobra é
  só de UX (o botão aparece, clicar dá 403), não de segurança. Fechar isso é tela por tela,
  registrado aqui pra não esquecer, não bloqueia esta entrega.

## Matriz de QA (usar pra testar manualmente e pra guiar os testes automatizados)

| Ação | Gestor | Despachante | Visualizador |
|---|---|---|---|
| Ver frota, motoristas, custos, OS, manutenção, relatórios, parceiros, pontos de recarga/coleta, passageiros | ✅ | ✅ | ✅ |
| Ver Assinatura/Billing | ✅ | ❌ (403 + escondido do menu) | ❌ (403 + escondido do menu) |
| Ver lista de rotas | ✅ | ✅ | ✅ |
| Criar rota / sugerir ordem | ✅ | ✅ | ❌ (403 + botão escondido) |
| Atribuir motorista a uma rota | ✅ | ✅ | ❌ |
| Cancelar rota | ✅ | ❌ (403 + botão escondido) | ❌ |
| Criar/editar/excluir veículo, motorista, custo, OS, ponto de coleta, etc. | ✅ | ❌ (403 — botão continua visível nesta fatia) | ❌ (idem) |
| Cadastrar passageiro novo (contato de parada) | ✅ | ✅ (precisa pra montar rota) | ❌ |
| Editar/excluir passageiro do cadastro | ✅ | ❌ | ❌ |
| Convidar/gerenciar equipe | ✅ | ❌ (403 + tela escondida) | ❌ (idem) |
| Mudar de plano / abrir portal de pagamento | ✅ | ❌ | ❌ |
| Aceitar convite de equipe e definir senha | — | — | (qualquer papel, é o próprio aceite) |

## Casos de borda (QA)

- Convite pra e-mail que já é login de alguém (gestor, despachante, motorista) → rejeita
  antes de mandar e-mail, mesma mensagem de `EmailAlreadyUsedException` já usada no convite
  de motorista.
- Token de convite expirado ou já usado → mesma `InvalidDriverInviteTokenException`-like
  (nova exceção equivalente pro convite de equipe), mensagem clara, não quebra a tela.
- Gestor tenta mudar o próprio papel via `PUT /v1/team/{userId}/role` → rejeita (não pode
  se auto-rebaixar por esse endpoint; se quiser sair, é `DELETE` de outro dono, ou não sai).
- Gestor tenta promover alguém a `GESTOR_FROTA`/`ADMIN` via `PUT /v1/team/{userId}/role` →
  rejeita (o enum de papéis aceitos nesse endpoint é só `VISUALIZADOR`/`DESPACHANTE`).
- Despachante tenta `POST /v1/team/invite` direto pela API (sem passar pela tela, que
  esconde o botão) → 403.
- Despachante tenta `POST /v1/routes/plans/{id}/cancel` direto pela API → 403.
- Visualizador tenta `POST /v1/routes/plans` direto pela API → 403.
- Tenant com só 1 pessoa (Gestor) continua funcionando exatamente igual — nenhuma mudança
  de comportamento pra quem nunca convidar ninguém.
- Remover (`DELETE /v1/team/{userId}`) um integrante que tem sessão ativa → próxima
  chamada autenticada dele falha (usuário desabilitado — mesma checagem que já existe pra
  conta desabilitada em outros fluxos).

## Achados durante a implementação (não estavam no desenho original)

- **Bug pré-existente: papel sem permissão devolvia 401, não 403.** `SecurityConfig` usava
  `response.sendError(...)` tanto pro "não autenticado" quanto pro "sem permissão" — sem um
  `accessDeniedHandler` explícito, e pior: `sendError` dispara o redirecionamento de página
  de erro do Tomcat pra `/error`, que reentra na cadeia de filtros de segurança e
  **sobrescreve o status original** com 401 do `authenticationEntryPoint`. Reproduzido ao
  vivo antes do fix (Despachante batendo em `/v1/billing/subscription` recebia 401, não
  403) e confirmado com um endpoint antigo já testado (`/v1/drivers` com token MOTORISTA —
  mesmo sintoma, então não era regressão desta spec). Corrigido escrevendo a resposta direto
  no `HttpServletResponse` (`setStatus` + corpo JSON com charset UTF-8 explícito — sem isso
  acentuação saía corrompida), sem `sendError`. Importa porque o front trata 401 como "sessão
  expirou, desloga e manda pro login" — sem o fix, todo Despachante/Visualizador barrado por
  papel seria deslogado em vez de ver "sem permissão".
- **`DriverController`, `BudgetController`, `CollectionPointController`, `PassengerController`
  tinham leitura mais fechada do que a própria matriz de QA deste documento previa.** A
  maioria dos endpoints de leitura do sistema já não tinha `@PreAuthorize` (aberto a
  qualquer autenticado), mas esses quatro tinham — `DriverController.list/get/license-
  expiring` era Gestor-only por uma correção de segurança anterior (não relacionada a esta
  spec), e os outros três tinham `@PreAuthorize` de **classe** herdado do padrão usado
  quando cada um foi criado. Descoberto ao testar o papel Despachante de verdade no
  navegador: o Dashboard mostrava "0 veículos" pro mesmo tenant que o Gestor via 20 mil.
  Abertos pros três papéis de gestão (leitura); escrita continua Gestor-only nos quatro,
  exceto `PassengerController.create`, que também abre pro Despachante (ele monta rota e
  precisa poder cadastrar um contato novo na hora). `DriverRatingController` (avaliação
  privada de motorista, spec 06) foi revisado e **mantido** Gestor-only de propósito — é
  dado sensível que não estava implícito em "ver motoristas" da matriz.
- **`DashboardPage.tsx` usava `Promise.all` misturando endpoints com permissão diferente.**
  Um único 403 (`drivers.licenseExpiring`, antes de abrir pra Despachante) derrubava a
  promise inteira e zerava todos os cards, não só o widget sem permissão — reproduzido ao
  vivo antes do fix acima resolver a causa raiz. Trocado por `Promise.allSettled` de
  qualquer forma, como defesa em profundidade (falha de rede parcial não devia derrubar a
  tela inteira, independente de ser 403 ou qualquer outro erro).

## Definition of Done

- [x] `Role` ganha `DESPACHANTE`/`VISUALIZADOR`.
- [x] `team_invite` (migration V30), `TeamService`, `TeamController` (invite/list/mudar
      papel/remover).
- [x] `POST /v1/auth/accept-team-invite`.
- [x] `RoutePlanController`: `list`/`suggest-order`/`create`/`assign` abrem pra
      `DESPACHANTE`; `cancel` continua fechado.
- [x] `BillingController` (subscription/checkout/portal) ganha `@PreAuthorize` explícito
      por método (achado da auditoria — não podia ser por classe, quebraria o webhook da
      Stripe, que não tem JWT).
- [x] `DriverController`/`BudgetController`/`CollectionPointController`/`PassengerController`:
      leitura aberta aos três papéis (achado ao testar de verdade, não previsto no desenho
      original — ver seção acima).
- [x] Bug de status 401×403 em `SecurityConfig` corrigido (achado ao testar — ver seção
      acima).
- [x] Front: `RequireGestor` aceita os três papéis; `RequireGestorTotal` (novo) restringe
      Assinatura e Equipe ao Gestor; tela "Equipe" (convidar/listar/mudar papel/remover);
      "Nova rota" escondido do Visualizador, "Cancelar rota" escondido do Despachante.
- [x] `DashboardPage` migrado pra `Promise.allSettled` (achado ao testar — ver seção acima).
- [x] Matriz de QA verificada manualmente ponta a ponta — backend via curl com token real
      (Gestor/Despachante/Visualizador, incluindo os casos de borda de convite) e front no
      navegador (convite → aceite → login → menu correto pro papel → Dashboard com dado
      real, não zerado).
- [ ] Fast-follow (não bloqueia esta entrega): esconder botão de criar/editar/excluir nas
      telas fora de rotas pro Despachante/Visualizador — o backend já bloqueia a escrita
      (403), o que falta é só a tela não mostrar um botão que vai dar erro.
