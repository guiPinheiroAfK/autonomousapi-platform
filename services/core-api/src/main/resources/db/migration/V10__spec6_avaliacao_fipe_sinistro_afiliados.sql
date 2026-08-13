-- V10 — spec 06 (parcerias e dados futuros), exceto recarga elétrica (sem provedor de
-- dado real disponível ainda — fica para quando existir parceria/API para agregar).
--
-- Mesma convenção do resto do schema `core`: tabelas "filhas" (rating, incidente, valor
-- de mercado) não têm tenant_id próprio — o isolamento vem de sempre resolver o pai
-- (driver/vehicle) escopado ao tenant antes de tocar nelas (ver VehicleCostEntry).
-- affiliate_click é exceção: não tem pai por tenant, então carrega tenant_id direto.

-- === Avaliação manual de motorista (spec 06 item 3) ===============================
create table driver_rating_manual (
    id              uuid primary key,
    driver_id       uuid not null references driver(id),
    gestor_user_id  uuid not null references app_user(id),
    nota            smallint not null check (nota between 1 and 5),
    comentario      varchar(500),
    created_at      timestamptz not null default now()
);
create index idx_driver_rating_manual_driver on driver_rating_manual (driver_id);

-- Agregado — nunca calculado on-the-fly na leitura (mesmo padrão do road_readiness_score
-- no geo-api). Recalculado a cada novo lançamento manual (ver DriverRatingService).
create table driver_rating_summary (
    id                 uuid primary key,
    driver_id          uuid not null unique references driver(id),
    nota_media         numeric(3, 2) not null,
    total_avaliacoes   integer not null,
    updated_at         timestamptz not null default now()
);

-- === Valor de mercado / FIPE (spec 06 item 2) ======================================
-- Cache do valor consultado, não uma tabela própria de FIPE (spec: "nunca chamada em
-- tempo real na tela do usuário"). Nesta rodada, lançamento é manual pelo gestor —
-- integração com uma API pública de FIPE fica para quando o matching marca/modelo do
-- catálogo FIPE (nomenclatura própria deles, não a nossa) for resolvido.
create table vehicle_market_value (
    id                 uuid primary key,
    vehicle_id         uuid not null references vehicle(id),
    valor_fipe         numeric(12, 2) not null,
    data_referencia    date not null,
    codigo_fipe        varchar(20),
    created_at         timestamptz not null default now()
);
create index idx_vehicle_market_value_vehicle on vehicle_market_value (vehicle_id);

-- === Sinistro e condição do veículo (spec 06 item 2) ===============================
create table vehicle_incident (
    id             uuid primary key,
    vehicle_id     uuid not null references vehicle(id),
    data           date not null,
    severidade     varchar(20) not null,
    descricao      varchar(500),
    custo_reparo   numeric(12, 2),
    created_at     timestamptz not null default now()
);
create index idx_vehicle_incident_vehicle on vehicle_incident (vehicle_id);

-- Agregado — nunca calculado on-the-fly (mesma regra do rating). Recalculado a cada
-- sinistro novo (ver VehicleConditionService). Fórmula v1 é heurística simples e
-- versionada, a calibrar com dado real (spec 06 é explícito sobre isso).
create table vehicle_condition_score (
    id                  uuid primary key,
    vehicle_id          uuid not null unique references vehicle(id),
    score               numeric(5, 2) not null,
    algorithm_version   varchar(20) not null,
    updated_at          timestamptz not null default now()
);

-- === Afiliados (spec 06 item 4) ====================================================
-- Catálogo de parceiros: não é por tenant (é a AutonomousAPI quem negocia a parceria).
create table affiliate_partner (
    id          uuid primary key,
    name        varchar(150) not null,
    category    varchar(40) not null,
    link_base   varchar(500) not null,
    created_at  timestamptz not null default now()
);

-- Clique é ação de um usuário de um tenant específico — carrega tenant_id direto
-- porque não tem um "pai" já escopado por tenant para herdar o isolamento.
create table affiliate_click (
    id          uuid primary key,
    tenant_id   uuid not null,
    partner_id  uuid not null references affiliate_partner(id),
    vehicle_id  uuid references vehicle(id),
    user_id     uuid not null references app_user(id),
    created_at  timestamptz not null default now()
);
create index idx_affiliate_click_tenant on affiliate_click (tenant_id);
create index idx_affiliate_click_partner on affiliate_click (partner_id);

-- affiliate_conversion fica para quando existir o primeiro parceiro real com
-- webhook/API de conversão (spec 06: "não assumir que clique = venda") — sem isso,
-- a tabela ficaria sem nenhum jeito real de ser preenchida.
