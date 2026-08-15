-- V13 — Ordens de Serviço (manutenção/oficina). Substitui o mock que vivia só no front
-- (apps/web/src/data/ordensServico.ts) — até aqui essa tela não persistia nada de verdade.
create table work_order (
    id                     uuid primary key,
    tenant_id              uuid not null,
    vehicle_id             uuid not null references vehicle(id),
    driver_id              uuid references driver(id),
    -- "OS-2026-0001": gerado no service a partir da contagem do tenant no ano — simples e
    -- suficiente neste volume; não é uma sequência atômica (risco de colisão sob concorrência
    -- alta é aceito nesta escala, mesmo raciocínio de outras simplificações do MVP).
    numero                 varchar(20) not null,
    tipo                   varchar(20) not null,
    status                 varchar(20) not null,
    prioridade             varchar(10) not null,
    descricao_problema     varchar(1000) not null,
    observacoes            varchar(1000),
    responsavel_oficina    varchar(150) not null,
    data_abertura          date not null,
    previsao_conclusao     date not null,
    km_abertura            integer not null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    unique (tenant_id, numero)
);
create index idx_work_order_tenant on work_order (tenant_id);
create index idx_work_order_vehicle on work_order (vehicle_id);

-- Itens/peças da OS — parte do agregado (sem lifecycle próprio, sempre criados/atualizados
-- junto do work_order que pertencem, mesmo padrão do restante do custo por km).
create table work_order_item (
    id                uuid primary key,
    work_order_id     uuid not null references work_order(id) on delete cascade,
    descricao         varchar(200) not null,
    quantidade        integer not null,
    valor_unitario    numeric(12, 2) not null
);
create index idx_work_order_item_work_order on work_order_item (work_order_id);
