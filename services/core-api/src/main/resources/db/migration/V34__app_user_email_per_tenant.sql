-- V34 — relaxa app_user.email de único global para único por tenant. Motivação real: um
-- Gestor tentou convidar alguém que já tinha conta em outra empresa e foi recusado — o
-- e-mail é uma identidade única na plataforma inteira hoje, não por empresa. Uma pessoa
-- passa a poder ter uma conta por tenant (papel e senha independentes em cada uma — o
-- aceite de convite já sempre pedia senha nova, nunca existiu senha compartilhada).

alter table app_user drop constraint app_user_email_key;

create unique index uq_app_user_tenant_email on app_user (tenant_id, email) where tenant_id is not null;

-- tenant_id nulo não é usado por nenhum código hoje (a coluna só permite por herança do
-- desenho original), mas UNIQUE(tenant_id, email) comum não pega colisão entre duas linhas
-- NULL — Postgres trata cada NULL como distinto numa constraint composta. Índice parcial
-- separado garante que, se um dia existir linha com tenant_id nulo, o e-mail continua único
-- entre elas.
create unique index uq_app_user_email_tenant_null on app_user (email) where tenant_id is null;
