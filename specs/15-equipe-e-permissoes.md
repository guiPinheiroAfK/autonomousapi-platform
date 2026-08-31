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
| Criar/editar/excluir veículo, motorista, custo, OS, ponto de coleta, passageiro, etc. | ✅ | ❌ (403 — botão continua visível nesta fatia) | ❌ (idem) |
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

## Definition of Done

- [ ] `Role` ganha `DESPACHANTE`/`VISUALIZADOR`.
- [ ] `team_invite` (migration), `TeamInviteService`, `TeamController` (invite/list/mudar
      papel/remover).
- [ ] `POST /v1/auth/accept-team-invite`.
- [ ] `RoutePlanController`: `list`/`suggest-order`/`create`/`assign` abrem pra
      `DESPACHANTE`; `cancel` continua fechado.
- [ ] `BillingController.subscription` ganha `@PreAuthorize` explícito (achado da
      auditoria, fechado de graça).
- [ ] Front: `RequireGestor` aceita os três papéis; Assinatura escondida de
      Despachante/Visualizador; tela "Equipe" (convidar/listar/mudar papel/remover);
      "Cancelar rota" escondido do Despachante.
- [ ] Matriz de QA acima verificada manualmente ponta a ponta (os 3 papéis × as ações da
      tabela).
- [ ] Registrado como fast-follow (não bloqueia esta entrega): esconder botão de
      criar/editar/excluir nas telas fora de rotas pro Despachante/Visualizador.
