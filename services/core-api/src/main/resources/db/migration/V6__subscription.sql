-- V6 — Assinatura (spec 03: billing web-first via Stripe, modelado para múltiplos canais).
create table subscription (
    id uuid primary key,
    tenant_id uuid not null unique references tenant (id),
    billing_source varchar(20) not null,
    status varchar(20) not null,
    stripe_customer_id varchar(255),
    stripe_subscription_id varchar(255),
    current_period_end timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
