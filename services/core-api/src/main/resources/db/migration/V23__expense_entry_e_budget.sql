-- V23 — Consolida vehicle_cost_entry em expense_entry (spec 10: categorias expandidas,
-- despesa de frota sem veículo, campos de combustível) e adiciona budget (spec 10),
-- custo_estimado/pricing_formula_version em route_plan e margem_padrao em tenant (spec 09).
--
-- expense_entry é a EVOLUÇÃO de vehicle_cost_entry, não uma tabela nova ao lado dela — ver
-- specs/09-custo-estimado-e-precificacao.md (amendment) e specs/10-gestao-de-custos.md,
-- item 1 ("fuel_entry não vira tabela separada, é expense_entry com categoria=combustivel").

create table expense_entry (
    id           uuid            primary key,
    tenant_id    uuid            not null references tenant (id),
    vehicle_id   uuid            references vehicle (id),   -- nullable: despesa de frota (ex. seguro corporativo)
    -- Valores em MAIÚSCULO: Hibernate (@Enumerated(EnumType.STRING)) grava o nome exato da
    -- constante Java (ExpenseCategory.COMBUSTIVEL, não "combustivel") — o CHECK precisa
    -- comparar com o que realmente chega na coluna, não com a convenção lower-case do resto
    -- do SQL deste arquivo.
    categoria    varchar(20)     not null
                 check (categoria in ('COMBUSTIVEL','MANUTENCAO','SEGURO','IPVA','MULTA','PEDAGIO','LAVAGEM','OUTRO')),
    valor        numeric(12,2)   not null,
    data         date            not null,
    descricao    varchar(255),
    -- 'ROUTE_PLAN' é reservado para quando um TRANSFER concluído gerar despesa automática —
    -- nenhum produtor existe ainda (ver RoutePlanService), não é campo morto/esquecido.
    fonte        varchar(20)     not null default 'MANUAL' check (fonte in ('MANUAL','ROUTE_PLAN')),
    litros_ou_kwh   numeric(10,3),  -- só quando categoria = COMBUSTIVEL
    odometro        integer,        -- só quando categoria = COMBUSTIVEL
    created_at   timestamptz     not null default now(),
    -- Trava de banco pros campos condicionais — mesma disciplina já usada nas validações de
    -- data em route_plan: validação só em Java não é validação de verdade.
    constraint chk_expense_entry_combustivel_fields check (
        categoria = 'COMBUSTIVEL' or (litros_ou_kwh is null and odometro is null)
    )
);
create index idx_expense_entry_tenant on expense_entry (tenant_id, data);
create index idx_expense_entry_vehicle on expense_entry (vehicle_id) where vehicle_id is not null;

-- left join de propósito, não inner: um inner join descartaria silenciosamente qualquer
-- vehicle_cost_entry cujo veículo já tenha sido deletado, e o drop table logo abaixo tornaria
-- essa perda irreversível sem ninguém perceber. Com left join, uma linha órfã de veículo
-- também fica órfã de tenant_id — inaceitável (tenant_id is not null) — então a migration
-- FALHA alto e visível em vez de silenciosamente descartar dado.
-- vce.category já vem em maiúsculo (era @Enumerated(EnumType.STRING) também) — copiado como
-- está, sem lower()/upper(), para bater com o CHECK acima.
insert into expense_entry (id, tenant_id, vehicle_id, categoria, valor, data, descricao, fonte, created_at)
select vce.id, v.tenant_id, vce.vehicle_id, vce.category, vce.amount, vce.occurred_at, vce.description, 'MANUAL', vce.created_at
from vehicle_cost_entry vce left join vehicle v on v.id = vce.vehicle_id;

-- Assert de contagem: se isso disparar, há vehicle_cost_entry órfã de verdade — resolver
-- manualmente (decidir descartar ou religar) antes de deixar essa migration seguir, não
-- silenciar o check.
do $$
declare
    origem_count int;
    destino_count int;
begin
    select count(*) into origem_count from vehicle_cost_entry;
    select count(*) into destino_count from expense_entry;
    if origem_count <> destino_count then
        raise exception 'expense_entry migration: % linhas em vehicle_cost_entry, % copiadas — vehicle_id orfao encontrado', origem_count, destino_count;
    end if;
end $$;

drop table vehicle_cost_entry;

create table budget (
    id             uuid          primary key,
    tenant_id      uuid          not null references tenant (id),
    vehicle_id     uuid          references vehicle (id),  -- nullable: orçamento de frota inteira
    categoria      varchar(20)
                   check (categoria is null or categoria in ('COMBUSTIVEL','MANUTENCAO','SEGURO','IPVA','MULTA','PEDAGIO','LAVAGEM','OUTRO')),
    periodo        varchar(10)   not null default 'MENSAL',
    valor_limite   numeric(12,2) not null,
    -- Dedup do BudgetAlertJob: guarda o último patamar já notificado (null|'80'|'100') pra só
    -- notificar de novo na TRANSIÇÃO de patamar, não a cada run do job — orçamento estourado é
    -- estado persistente (dura o mês inteiro), diferente de CNH vencendo (a janela de aviso
    -- acaba passando); sem isso, o alerta repetiria diariamente por semanas.
    ultimo_patamar_notificado varchar(3),
    periodo_referencia varchar(7), -- 'YYYY-MM' do período atual — reseta ultimo_patamar_notificado na virada
    created_at     timestamptz   not null default now()
);
create index idx_budget_tenant on budget (tenant_id);

alter table route_plan add column custo_estimado numeric(10,2);
alter table route_plan add column pricing_formula_version varchar(10);

-- Margem por tenant (spec 09: "margem configurável por tenant", não um número global fixo).
alter table tenant add column margem_padrao numeric(5,4) not null default 0.20;

-- Preço de referência de combustível/energia (spec 09) — mesmo tratamento manual já dado à
-- FIPE (spec 06, item 2): tabela pequena, atualizada manualmente, sem integração de API.
-- Um tipo por linha, incluindo 'eletrico' (preço do kWh, não litro).
create table fuel_price_reference (
    id                uuid          primary key,
    tenant_id         uuid          not null references tenant (id),
    tipo_combustivel  varchar(20)   not null,
    preco             numeric(8,3)  not null,
    data_atualizacao  date          not null,
    unique (tenant_id, tipo_combustivel)
);
