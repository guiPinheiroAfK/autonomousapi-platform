-- V21 — Pontos de coleta reutilizáveis (spec 08 item 5) e route_plan.categoria pra
-- distinguir rota multi-parada (ROTA) de trajeto único com valor combinado (TRANSFER).

create table collection_point (
    id                  uuid            primary key,
    tenant_id           uuid            not null references tenant (id),
    nome                varchar(200)    not null,
    endereco            varchar(300)    not null,
    lat                 double precision not null,
    lon                 double precision not null,
    janela_inicio       time,
    janela_fim          time,
    -- true quando o gestor arrasta o pino manualmente pra corrigir geocodificação
    -- imprecisa (spec 08 item 5) — útil depois pra saber onde a precisão do cadastro
    -- é mais confiável.
    posicao_ajustada    boolean         not null default false,
    -- soft delete: route_stop.collection_point_id pode referenciar, nunca DELETE físico.
    ativo               boolean         not null default true,
    created_at          timestamptz     not null default now()
);
create index idx_collection_point_tenant on collection_point (tenant_id);

alter table route_plan add column categoria varchar(10) not null default 'ROTA';
alter table route_plan add column valor numeric(10,2);

-- data_execucao precisa ser not null, mas as linhas de teste do PR #55 não têm esse dado
-- ainda — backfill a partir de created_at antes de travar a constraint.
alter table route_plan add column data_execucao date;
update route_plan set data_execucao = created_at::date where data_execucao is null;
alter table route_plan alter column data_execucao set not null;

alter table route_stop add column collection_point_id uuid references collection_point (id);
