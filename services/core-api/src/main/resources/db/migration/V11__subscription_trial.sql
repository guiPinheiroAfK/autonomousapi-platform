-- V11 — Trial de 7 dias (spec 03: "nunca liberar feature checando estado local, sempre
-- validar subscription.status via core-api"). Até aqui nada aplicava essa regra: um
-- tenant sem assinatura ativa conseguia usar o sistema à vontade para sempre.
--
-- trial_ends_at é controlado só pelo core-api (nunca pela Stripe) — é o relógio do
-- período grátis antes de exigir cartão. Quando o tenant completa o Checkout de verdade,
-- o status vira ACTIVE via webhook e este campo deixa de importar.
alter table subscription
    add column trial_ends_at timestamptz;
