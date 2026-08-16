-- V17 — Mini-chat gestor↔motorista, retenção híbrida (ADR 0015). O servidor guarda só
-- uma janela recente por conversa (últimas 50 mensagens OU últimos 7 dias, o que vier
-- primeiro); o histórico completo vive no dispositivo do gestor. `ainda_no_servidor`
-- controla o que já foi removido pelo job de limpeza — soft delete, não DELETE físico,
-- porque o job só pode agir depois de confirmar que o gestor já sincronizou aquele trecho
-- (chat_sync_cursor), e remover a linha destruiria essa auditoria a meio caminho.

create table chat_conversation (
    id              uuid        primary key,
    tenant_id       uuid        not null references tenant (id),
    gestor_user_id  uuid        not null references app_user (id),
    driver_id       uuid        not null references driver (id),
    vehicle_id      uuid        references vehicle (id),
    created_at      timestamptz not null default now(),
    constraint uq_chat_conversation_par unique (gestor_user_id, driver_id)
);

create index idx_chat_conversation_tenant on chat_conversation (tenant_id);
create index idx_chat_conversation_driver on chat_conversation (driver_id);

create table chat_message (
    id                  uuid        primary key,
    conversation_id     uuid        not null references chat_conversation (id),
    sender_user_id      uuid        not null references app_user (id),
    body                varchar(2000) not null,
    sent_at             timestamptz not null default now(),
    delivered_at        timestamptz,
    lido_em             timestamptz,
    ainda_no_servidor   boolean     not null default true
);

create index idx_chat_message_conversation on chat_message (conversation_id, sent_at desc);

-- Cursor por dispositivo do gestor (não por conversa — um device sincroniza tudo de uma
-- vez). O job de limpeza só remove mensagens já cobertas por TODOS os devices do gestor.
create table chat_sync_cursor (
    id              uuid        primary key,
    gestor_user_id  uuid        not null references app_user (id),
    device_id       varchar(200) not null,
    synced_at       timestamptz not null,
    updated_at      timestamptz not null default now(),
    constraint uq_chat_sync_cursor_device unique (gestor_user_id, device_id)
);

create index idx_chat_sync_cursor_gestor on chat_sync_cursor (gestor_user_id);
