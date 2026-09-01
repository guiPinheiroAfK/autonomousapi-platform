-- V31 — Responder, editar, excluir, encaminhar e reagir no chat (pedido do Guilherme,
-- "abrir um PR do chat"). Editar/excluir/reagir só valem enquanto a mensagem ainda está
-- `ainda_no_servidor = true` (mesma janela que o ChatCleanupJob já respeita, ADR 0015) —
-- fora dela o outro lado não teria como ver a mudança (o poll só busca mensagens ainda no
-- servidor). Responder e encaminhar não têm essa restrição: os dois criam uma linha nova
-- (a resposta guarda um retrato do texto original no momento do envio, não uma referência
-- viva que precisaria ser buscada de novo depois).

alter table chat_message
    add column edited_at                timestamptz,
    add column deleted_at               timestamptz,
    add column reply_to_message_id      uuid references chat_message (id),
    add column reply_to_body_snapshot   varchar(200),
    add column reply_to_sender_user_id  uuid,
    add column forwarded_from_message_id uuid;

-- Uma reação por pessoa por mensagem — tocar de novo no mesmo emoji remove (DELETE), tocar
-- em outro substitui (upsert no service). Igual WhatsApp.
create table chat_message_reaction (
    id          uuid        primary key,
    message_id  uuid        not null references chat_message (id),
    user_id     uuid        not null references app_user (id),
    emoji       varchar(8)  not null,
    created_at  timestamptz not null default now(),
    constraint uq_chat_message_reaction unique (message_id, user_id)
);

create index idx_chat_message_reaction_message on chat_message_reaction (message_id);
