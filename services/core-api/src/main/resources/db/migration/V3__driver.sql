-- V3 — Schema core: cadastro de motoristas da frota (Fase 1, spec 05).
-- Registro operacional (nome, CNH, telefone) mantido pelo gestor — NÃO é a conta
-- de login do motorista (isso é core.app_user, role MOTORISTA). Vincular um
-- motorista a um usuário com login é feature futura (fluxo de convite).

create table driver (
    id         uuid         primary key,
    tenant_id  uuid         not null references tenant (id),
    name       varchar(150) not null,
    cnh        varchar(11)  not null,
    phone      varchar(20),
    -- ATIVO, INATIVO
    status     varchar(20)  not null default 'ATIVO',
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint uq_driver_tenant_cnh unique (tenant_id, cnh)
);

create index idx_driver_tenant on driver (tenant_id);
