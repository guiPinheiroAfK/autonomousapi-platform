-- V14 — Recuperação de senha. Mesmo padrão de token do email_verification_token (V12):
-- hash SHA-256, nunca o valor cru, curta duração. Tabela própria (não reaproveita
-- email_verification_token) porque são propósitos diferentes — misturar os dois
-- complicaria a semântica de "usável" de cada um.
create table password_reset_token (
    id          uuid primary key,
    user_id     uuid not null references app_user(id),
    token_hash  varchar(64) not null unique,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_password_reset_token_user on password_reset_token (user_id);
