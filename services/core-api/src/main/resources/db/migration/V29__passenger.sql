-- V29 — Cadastro reutilizável de passageiro/cliente final (spec 14). Dado de terceiro sem
-- consentimento direto — sem coluna `ativo`: exclusão é real (ver PassengerService), não
-- soft-delete.

create table passenger (
    id          uuid        primary key,
    tenant_id   uuid        not null references tenant (id),
    nome        varchar(200) not null,
    telefone    varchar(30)  not null,
    created_at  timestamptz not null default now()
);

create index idx_passenger_tenant on passenger (tenant_id);

-- ON DELETE SET NULL: excluir um passageiro do cadastro não pode apagar nem travar a
-- exclusão de uma parada/rota já concluída — o vínculo só é um atalho de leitura.
alter table route_stop add column passenger_id uuid references passenger (id) on delete set null;
