-- V8 — Índices nos caminhos de consulta que já existem em produção (auditoria de performance).
-- Nenhuma mudança de schema: só cobre query que hoje faz sequential scan.

-- O webhook da Stripe resolve a assinatura por stripe_customer_id a cada evento
-- (BillingService#onSubscriptionUpdated). Chamada por sistema externo, sem índice
-- até aqui — vira scan da tabela inteira conforme a base de assinantes cresce.
create index idx_subscription_stripe_customer on subscription (stripe_customer_id);

-- Dashboard (cost-trend) e export de CSV filtram custo por veículo E por data
-- (occurred_at >= :since). O índice existente cobria só vehicle_id; o composto
-- serve as duas queries e mantém a ordenação por data sem sort adicional.
create index idx_vehicle_cost_entry_vehicle_occurred on vehicle_cost_entry (vehicle_id, occurred_at);

-- Viagens por veículo (histórico do veículo, e futura agregação da Fase 2).
create index idx_trip_vehicle on trip (vehicle_id);
