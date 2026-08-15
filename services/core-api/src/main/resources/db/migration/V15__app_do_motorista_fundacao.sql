-- V15 — Fundação do app do motorista (spec 07): vínculo driver↔login, convite por
-- email e designação motorista→veículo. Sem estas três peças, nada do spec 07 é
-- implementável: "meu veículo", "minha CNH" e as regras de segurança ("filtrar pelo
-- motorista do token") todas dependem de resolver qual `driver` está por trás de um
-- login MOTORISTA. Ver ADR 0013 (vínculo + convite) e ADR 0014 (assignment).

-- 1) Vínculo do registro operacional com a conta de login (opcional: a maioria dos
--    motoristas não terá login). app_user_id único garante 1 login por motorista e
--    1 motorista por login. email fica no driver para permitir (re)convite e exibição.
alter table driver add column email        varchar(255);
alter table driver add column app_user_id  uuid unique references app_user (id);

-- 2) Convite: mesmo padrão de token do password_reset_token (V14) — hash SHA-256,
--    nunca o valor cru, curta duração, tabela própria por ter semântica de "usável"
--    diferente. O aceite cria o app_user (role MOTORISTA) e preenche driver.app_user_id.
create table driver_invite (
    id          uuid         primary key,
    driver_id   uuid         not null references driver (id),
    token_hash  varchar(64)  not null unique,
    expires_at  timestamptz  not null,
    used_at     timestamptz,
    created_at  timestamptz  not null default now()
);

create index idx_driver_invite_driver on driver_invite (driver_id);

-- 3) Designação motorista→veículo, com histórico. ended_at nulo = designação ativa.
--    A pergunta "qual é o meu veículo agora?" é a designação ativa do motorista.
--    Índices parciais garantem no máximo uma designação ativa por motorista e por
--    veículo (um veículo não é dirigido por dois motoristas ao mesmo tempo neste modelo).
create table driver_vehicle_assignment (
    id          uuid         primary key,
    tenant_id   uuid         not null references tenant (id),
    driver_id   uuid         not null references driver (id),
    vehicle_id  uuid         not null references vehicle (id),
    started_at  timestamptz  not null default now(),
    ended_at    timestamptz,
    created_at  timestamptz  not null default now()
);

create index idx_dva_tenant on driver_vehicle_assignment (tenant_id);
create index idx_dva_driver on driver_vehicle_assignment (driver_id);
create index idx_dva_vehicle on driver_vehicle_assignment (vehicle_id);
create unique index uq_dva_driver_ativo on driver_vehicle_assignment (driver_id) where ended_at is null;
create unique index uq_dva_vehicle_ativo on driver_vehicle_assignment (vehicle_id) where ended_at is null;
