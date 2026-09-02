-- ADR 0025: permissão por módulo (ver/escrever) ajustável por usuário, por cima do padrão
-- do papel. Guarda só a DIFERENÇA em relação ao padrão — quem nunca teve ajuste não tem
-- linha nenhuma aqui, e trocar o papel da pessoa continua valendo sozinho.
create table user_permission_override
(
    id         uuid        primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    permission varchar(40) not null,
    allowed    boolean     not null,
    created_at timestamptz not null default now(),
    unique (user_id, permission)
);

-- on delete cascade acima é obrigatório, não conveniência: remover alguém da equipe tenta
-- um DELETE de verdade em app_user primeiro (V34/TeamService.remove) e cai pro "desativar"
-- se qualquer FK barrar — sem o cascade, a primeira permissão ajustada faria toda remoção
-- silenciosamente virar desativação.
create index idx_user_permission_override_user on user_permission_override (user_id);
