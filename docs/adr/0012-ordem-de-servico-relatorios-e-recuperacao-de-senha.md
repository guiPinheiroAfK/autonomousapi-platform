# ADR 0012 — Ordem de serviço real, relatórios reais e recuperação de senha

**Status:** aceito
**Data:** 2026-08-15

## Contexto

Auditoria da estrutura do front encontrou duas telas com aparência de produto real
rodando em cima de dado 100% mockado: `apps/web/src/data/ordensServico.ts` e
`data/financeiro.ts`, sem nenhuma entidade correspondente no backend. Junto disso, o
fluxo de auth ainda não tinha recuperação de senha — uma lacuna comum em "backend
redondo" que faltava fechar.

## Decisões

### Numeração de OS não é atômica

`WorkOrderService.gerarNumero` conta quantas OS o tenant já abriu naquele ano e soma 1
("OS-2026-0001"). Não é uma sequência de banco (`SERIAL`/`nextval`) — duas criações
simultâneas do mesmo tenant no mesmo instante podem, em teoria, gerar o mesmo número.
Aceito nesta escala (poucas OS por tenant, criadas por um gestor humano, não em lote) —
se isso virar operação em massa (import, integração externa), trocar por sequência real
por tenant antes, não depois de um caso real de colisão.

### Itens da OS não têm endpoint próprio

`WorkOrderItem` não tem controller nem repositório de escrita expostos — a lista inteira
de itens é substituída (`deleteAllByWorkOrderId` + recriar) a cada `PUT`. Simples e
correto no volume esperado (poucos itens por OS, editados junto do resto do formulário);
CRUD granular de item só valdria a pena se a tela precisasse editar item sem reabrir a OS
inteira, o que não é o caso hoje.

### Relatório de manutenção é calculado em memória, não em SQL agregado

`WorkOrderService.maintenanceSummary` busca todas as OS do tenant (com itens) e agrega em
Java, não com `GROUP BY` no banco. Mesmo raciocínio do `VehicleConditionService` e do
`road_readiness` do geo-api: no volume de uma frota (dezenas a poucas centenas de OS),
isso é mais simples de ler e testar que JPQL com múltiplos agrupamentos, e não é gargalo.
Reavaliar se o volume de OS por tenant crescer ordens de magnitude.

### Recuperação de senha: tabela própria, sem login automático

`PasswordResetToken` é uma tabela separada de `EmailVerificationToken` (ADR 0011), mesmo
padrão de hash SHA-256. Duas escolhas deliberadas, diferentes do fluxo de confirmação de
e-mail:

- **Não emite tokens de acesso ao confirmar.** Confirmar e-mail prova posse da caixa de
  entrada — razoável logar direto. Redefinir senha só prova a mesma coisa; exigir login
  com a senha nova depois confirma que a pessoa realmente escolheu e lembra a senha.
- **Revoga todos os refresh tokens do usuário** (`RefreshTokenRepository.revokeAllForUser`,
  já existia, criado para logout mas nunca usado até agora). Se um refresh token tivesse
  vazado, trocar a senha sem isso deixaria a sessão vazada viva.

`forgot-password` é silencioso sobre o e-mail existir ou não (sempre 202), mesmo
raciocínio do `resend-verification` da ADR 0011 — evita descobrir e-mail cadastrado por
tentativa.

## Consequências

- `data/ordensServico.ts` e `data/financeiro.ts` (front) ficam sem uso assim que as
  páginas forem religadas ao backend real — remoção é o próximo passo, não parte desta ADR.
- `GET /v1/reports/maintenance-summary` sempre olha os últimos 12 meses fechados a partir
  do mês corrente; o ranking de veículo é histórico completo (todo o período), replicando
  exatamente o escopo que o mock antigo já tinha.
