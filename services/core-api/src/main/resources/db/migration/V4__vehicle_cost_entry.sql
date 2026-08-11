-- V4 — Schema core: lançamentos de custo por veículo (custo por km, spec 05).
-- Sem tenant_id próprio: o isolamento por tenant vem de vehicle_id -> vehicle.tenant_id
-- (a aplicação sempre resolve o veículo escopado ao tenant antes de tocar nos custos).

create table vehicle_cost_entry (
    id          uuid          primary key,
    vehicle_id  uuid          not null references vehicle (id),
    -- COMBUSTIVEL, MANUTENCAO, OUTRO
    category    varchar(20)   not null,
    amount      numeric(12,2) not null,
    description varchar(255),
    occurred_at date          not null,
    created_at  timestamptz   not null default now()
);

create index idx_vehicle_cost_entry_vehicle on vehicle_cost_entry (vehicle_id);
