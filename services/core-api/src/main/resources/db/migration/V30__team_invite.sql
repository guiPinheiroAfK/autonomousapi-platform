-- V30 — Convite de equipe (spec 15): Despachante/Visualizador, papel restrito por tenant.
-- Mesmo desenho de token de driver_invite (V15), mas sem vínculo com um registro
-- operacional pré-existente — um novo integrante de equipe não tem "driver" prévio, então
-- email/nome vêm no próprio convite, não resolvidos de outra tabela.

create table team_invite (
    id                  uuid         primary key,
    tenant_id           uuid         not null references tenant (id),
    email               varchar(255) not null,
    nome                varchar(200) not null,
    role                varchar(20)  not null,
    invited_by_user_id  uuid         not null references app_user (id),
    token_hash          varchar(64)  not null unique,
    expires_at          timestamptz  not null,
    used_at             timestamptz,
    created_at          timestamptz  not null default now()
);

create index idx_team_invite_tenant on team_invite (tenant_id);
