-- V2 — Schema core: veículos da frota (Fase 1, spec 05).
-- Placa única POR TENANT (não globalmente) — cada tenant é uma frota isolada;
-- evita colisão entre dados de tenants diferentes sem depender de formato real de placa.

create table vehicle (
    id          uuid         primary key,
    tenant_id   uuid         not null references tenant (id),
    plate       varchar(10)  not null,
    brand       varchar(80)  not null,
    model       varchar(80)  not null,
    model_year  integer,
    odometer_km integer      not null default 0,
    -- ATIVO, MANUTENCAO, INATIVO
    status      varchar(20)  not null default 'ATIVO',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uq_vehicle_tenant_plate unique (tenant_id, plate)
);

create index idx_vehicle_tenant on vehicle (tenant_id);
