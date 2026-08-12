-- V7 — Registro de viagem do motorista (spec 03: mobile, offline-first no app,
-- core-api guarda só a sessão da viagem; pings brutos de GPS vivem no schema geo).
create table trip (
    id uuid primary key,
    tenant_id uuid not null,
    user_id uuid not null references app_user (id),
    vehicle_id uuid not null references vehicle (id),
    status varchar(20) not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    created_at timestamptz not null
);

create index idx_trip_tenant on trip (tenant_id);
create index idx_trip_user on trip (user_id);
