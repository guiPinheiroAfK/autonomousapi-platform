-- V27 — Notificação in-app (topbar do web): até aqui, o sino do topbar mostrava um "2"
-- fixo e dois itens estáticos de exemplo, sem nenhuma tabela por trás. Reaproveita os
-- mesmos disparos que já existiam pra push (BudgetAlertJob, AlertPushJob,
-- DriverNotificationService) — cada chamada de push agora também grava uma linha aqui,
-- pra alimentar a lista/contador reais no web.

create table notification (
    id          uuid        primary key,
    user_id     uuid        not null references app_user (id),
    tipo        varchar(40) not null,
    titulo      varchar(200) not null,
    corpo       text        not null,
    link        varchar(300),
    lida        boolean     not null default false,
    created_at  timestamptz not null default now()
);

create index idx_notification_user_created on notification (user_id, created_at desc);
create index idx_notification_user_unread on notification (user_id, lida) where lida = false;
