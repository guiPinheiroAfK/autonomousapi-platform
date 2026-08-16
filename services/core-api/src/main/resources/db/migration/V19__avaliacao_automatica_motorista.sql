-- V19 — Avaliação automática de motorista (spec 06, item 3): componente calculado a
-- partir do dado de condução (frenagem brusca, excesso de velocidade). "Desvio de rota"
-- fica de fora desta rodada — precisa de rota planejada como referência, que ainda não
-- existe (roteamento é o último pedaço da Fase 2, spec 05).

-- Marca a viagem como já processada pelo job de rating automático — evita reprocessar
-- a mesma viagem (e duplicar lançamento) a cada rodada do job.
alter table trip add column rating_processed_at timestamptz;

create table driver_rating_auto (
    id             uuid primary key,
    driver_id      uuid not null references driver(id),
    trip_id        uuid not null references trip(id),
    componente     varchar(30) not null,
    score          numeric(3, 2) not null,
    observado_em   timestamptz not null,
    created_at     timestamptz not null default now()
);

-- Um lançamento por (viagem, componente) — a chave que garante idempotência do job.
create unique index idx_driver_rating_auto_trip_componente on driver_rating_auto (trip_id, componente);
create index idx_driver_rating_auto_driver on driver_rating_auto (driver_id);
