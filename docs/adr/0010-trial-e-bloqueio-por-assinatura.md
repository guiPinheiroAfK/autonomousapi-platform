# ADR 0010 — Trial de 7 dias e bloqueio de escrita sem assinatura

**Status:** aceito
**Data:** 2026-08-13

## Contexto

Spec 03 é explícito: "Nunca liberar feature no app checando estado local — sempre validar
`subscription.status` via `core-api` (evita bypass e mantém uma única fonte de verdade)."
Até esta rodada, nada aplicava essa regra — nenhum service checava `subscription.status`
antes de aceitar criar veículo, motorista, lançar custo, etc. Um tenant sem nunca ter
assinado (ou com pagamento atrasado) usava o produto inteiro, para sempre, de graça.

## Decisão

**Trial de 7 dias criado automaticamente no signup**, sem tocar na Stripe: `AuthService`
grava um `Subscription` com `status = TRIALING` e `trial_ends_at = agora + 7 dias`. Depois
disso, sem uma assinatura `ACTIVE` de verdade (via Checkout real), escrita é bloqueada.

**Bloqueio é só de escrita, nunca de leitura.** `SubscriptionGate` (um `HandlerInterceptor`,
não um filtro de segurança) intercepta só `POST/PUT/PATCH/DELETE`. O tenant nunca perde
acesso ao que já cadastrou — só não consegue adicionar mais nada até resolver o pagamento.
Isso evita o pior cenário de suporte: gestor perde visão da própria frota por atraso.

**`/v1/billing/**` e `/v1/auth/**` são as únicas rotas fora do gate.** Sem essa exceção, um
tenant bloqueado nunca conseguiria chamar `POST /v1/billing/checkout` para se desbloquear —
o próprio mecanismo de sair do bloqueio ficaria preso atrás do bloqueio.

**Isso é "front técnico", não cobrança de verdade** (conforme combinado): nenhuma chamada à
Stripe acontece no signup. O trial e o bloqueio são inteiramente locais, controlados só pelo
core-api. O que vira dinheiro de verdade continua sendo exatamente o fluxo que já existia —
`POST /v1/billing/checkout` → Stripe Checkout hospedado (pede cartão de verdade) → webhook
`checkout.session.completed` → `Subscription.applyStripeUpdate(..., ACTIVE, ...)`. O trial só
precisa existir para o tenant ter 7 dias de uso antes de precisar passar por esse fluxo.

## Consequências

- `DemoDataSeeder` precisou de um `Subscription` `ACTIVE` explícito (sem isso o ambiente de
  demonstração ficaria bloqueado depois de qualquer criação de tenant nova — ele não passa
  por `AuthService.signup`, então não ganha trial sozinho).
- Tenants criados **antes** desta migration não têm `Subscription` nenhuma — `SubscriptionGate`
  trata "sem assinatura" como "não pode escrever" (`Optional.orElse(false)`), então esses
  tenants ficam bloqueados até alguém criar a assinatura manualmente ou eles completarem um
  checkout real. Não escrevemos um backfill automático: decidir a assinatura de um tenant
  existente sem saber o histórico real dele é chute, não migração.
- Quando existir cobrança real (chaves de Stripe em produção), nada nesta ADR muda — o trial
  continua a mesma janela de 7 dias antes do primeiro Checkout.
