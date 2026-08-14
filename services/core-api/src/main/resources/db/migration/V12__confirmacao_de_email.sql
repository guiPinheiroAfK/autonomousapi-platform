-- V12 — Confirmação de e-mail no cadastro (ADR 0011).
--
-- app_user.enabled já existia (V1) mas nunca era usado no signup — todo usuário nascia
-- habilitado. A partir daqui, signup cria o usuário DESABILITADO; só o clique no link do
-- e-mail (via este token) habilita. Login já rejeitava usuário desabilitado (AuthService),
-- então essa metade da trava já existia — faltava só a outra ponta.
create table email_verification_token (
    id          uuid primary key,
    user_id     uuid not null references app_user(id),
    token_hash  varchar(64) not null unique,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_email_verification_token_user on email_verification_token (user_id);
