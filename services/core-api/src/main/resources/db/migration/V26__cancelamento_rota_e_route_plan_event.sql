-- V26 — Cancelamento de rota (ADR 0021) e telemetria de uso do trâmite (ADR 0020,
-- route_plan_event). route_plan_event fica no schema core (não geo, ao contrário do que
-- specs/11-caminho-feliz-rotas.md tinha proposto originalmente): core-api é dono exclusivo
-- do schema core e é quem detecta cada transição — gravar na mesma transação do próprio
-- route_plan garante que o evento nunca diverge do estado real (ver ADR 0020).

-- CANCELADA junto de PLANEJADA/EM_ANDAMENTO/CONCLUIDA — mesmo padrão dos demais status,
-- sem CHECK constraint (validado em RoutePlanStatus, igual ao resto do schema).

create table route_plan_event (
    id              uuid        primary key,
    route_plan_id   uuid        not null references route_plan (id),
    tipo            varchar(40) not null,
    ator_user_id    uuid        references app_user (id),
    metadado        jsonb,
    created_at      timestamptz not null default now()
);

create index idx_route_plan_event_plan on route_plan_event (route_plan_id, tipo, created_at);
