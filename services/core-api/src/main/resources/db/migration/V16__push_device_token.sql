-- V16 — Notificações push (ADR 0016). Registro de device token por usuário; a entrega
-- em si é feita pelo PushSender (Logging em dev, Expo quando configurado), mesmo padrão
-- do EmailSender/BillingService com a Stripe.

-- Token é a chave natural: um token repetido é upsert (mesmo dispositivo, plataforma
-- pode ter trocado de usuário — ex. logout/login de outro motorista no mesmo aparelho).
create table push_device_token (
    id          uuid         primary key,
    user_id     uuid         not null references app_user (id),
    token       varchar(255) not null unique,
    -- ANDROID, IOS, WEB
    plataforma  varchar(20)  not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create index idx_push_device_token_user on push_device_token (user_id);
