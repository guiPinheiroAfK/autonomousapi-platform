-- V20 — Rota multi-parada (spec 02 "Roteamento com múltiplos pontos") e mensagem
-- estruturada de chat pra designar rota direto da conversa (spec 07 item 8).
--
-- `route_plan.status` nunca é escrito por fora do RoutePlanService.completeStop: nasce
-- PLANEJADA, vira EM_ANDAMENTO sozinho na primeira parada concluída e CONCLUIDA sozinho
-- quando a última parada pendente é concluída — é sempre derivado do estado das paradas,
-- nunca um campo que o gestor edita manualmente.
--
-- `route_stop.ordem_sugerida` é a ordem final que o gestor confirmou na tela (a sugestão
-- da heurística é só um ponto de partida revisável, nunca persistida por si só — ver
-- RoutePlanService.suggestOrder, que é stateless).

create table route_plan (
    id              uuid        primary key,
    tenant_id       uuid        not null references tenant (id),
    gestor_user_id  uuid        not null references app_user (id),
    driver_id       uuid        references driver (id),
    vehicle_id      uuid        references vehicle (id),
    status          varchar(20) not null default 'PLANEJADA',
    created_at      timestamptz not null default now()
);

create index idx_route_plan_tenant on route_plan (tenant_id);
create index idx_route_plan_driver on route_plan (driver_id) where driver_id is not null;

create table route_stop (
    id                      uuid            primary key,
    route_plan_id           uuid            not null references route_plan (id),
    tipo                    varchar(10)     not null, -- COLETA, ENTREGA
    label                   varchar(300)    not null, -- displayName do Nominatim (ADR 0018)
    lat                     double precision not null,
    lon                     double precision not null,
    janela_inicio           time,
    janela_fim              time,
    ordem_sugerida          int             not null,
    ordem_real_executada    int,
    concluida_em            timestamptz
);

create index idx_route_stop_plan on route_stop (route_plan_id, ordem_sugerida);

-- Mensagem estruturada: hoje só existe TEXT (body livre). ROUTE_ASSIGNMENT referencia um
-- route_plan e é gerada pelo backend (ChatService.sendRoutePlanMessage), nunca digitada
-- pelo usuário — body continua preenchido com um texto de fallback pra quem não interpreta
-- o tipo (ex. notificação push, cliente desatualizado).
alter table chat_message add column message_type varchar(20) not null default 'TEXT';
alter table chat_message add column route_plan_id uuid references route_plan (id);
