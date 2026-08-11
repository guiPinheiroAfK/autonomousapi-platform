-- V1 — Schema core: tenants, usuários (multi-perfil) e refresh tokens.
-- Flyway cria o schema `core` (create-schemas) e roda esta migration dentro dele.
-- FK dentro do schema core é permitida; FK cross-schema para `geo` é PROIBIDA (ADR 0004).

create table tenant (
    id         uuid         primary key,
    name       varchar(200) not null,
    created_at timestamptz  not null default now()
);

create table app_user (
    id            uuid         primary key,
    tenant_id     uuid         references tenant (id),
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    -- Perfis (spec 01): gestor_frota, motorista, admin, parceiro_api
    role          varchar(32)  not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now()
);

create index idx_app_user_tenant on app_user (tenant_id);

create table refresh_token (
    id         uuid         primary key,
    user_id    uuid         not null references app_user (id),
    -- Guardamos apenas o hash SHA-256 (hex) do token, nunca o valor em claro.
    token_hash varchar(64)  not null unique,
    expires_at timestamptz  not null,
    revoked    boolean      not null default false,
    created_at timestamptz  not null default now()
);

create index idx_refresh_token_user on refresh_token (user_id);
