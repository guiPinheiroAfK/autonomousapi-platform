-- Massa de dados pra teste de carga — não é o seed funcional de scripts/test-e2e (que passa
-- por API de propósito, pra exercitar validação). Aqui o objetivo é só volume de linhas: gerar
-- milhares de veículos/despesas rápido o bastante pra caber num teste de carga, então vai direto
-- no banco via generate_series em vez de milhares de requisições HTTP sequenciais.
--
-- Pré-requisito: um tenant já existente (criado via scripts/test-e2e/01-seed.sh, que passa pela
-- API de verdade — hash de senha, confirmação de e-mail etc.). Este script só adiciona volume
-- de vehicle/expense_entry em cima desse tenant.
--
-- Uso:
--   docker exec -i autonomousapi-db-1 psql -U autonomousapi -d autonomousapi \
--     -v tenant_id="'<uuid-do-tenant>'" -v n_vehicles=2000 -v expenses_per_vehicle=5 \
--     -f scripts/stress-test/seed-bulk.sql

-- Placa determinística por linha (LT000001..LTxxxxxx) — nunca colide com a
-- uq_vehicle_tenant_plate, então não precisa de retry nem checar duplicata.
insert into core.vehicle (id, tenant_id, plate, brand, model, model_year, odometer_km,
                           odometro_inicial, status, atributos, created_at, updated_at)
select
    gen_random_uuid(),
    :tenant_id,
    'LT' || lpad(gs::text, 6, '0'),
    (array['Fiat','Volkswagen','Chevrolet','Renault','Iveco'])[1 + (gs % 5)],
    (array['Fiorino','Strada','Saveiro','Kangoo','Daily'])[1 + (gs % 5)],
    2018 + (gs % 7),
    20000 + (gs * 37 % 80000),
    -- odometro_inicial abaixo do atual (km rodado desde o cadastro > 0), pra exercitar o
    -- cálculo real de custoPorKm sob carga, não o caso "sem km rodado ainda" (retorna null
    -- cedo, então não bate na query de agregação de despesas do jeito que a carga real bate).
    greatest(0, 20000 + (gs * 37 % 80000) - (1000 + gs % 15000)),
    'ATIVO',
    '{}'::jsonb,
    now(),
    now()
from generate_series(1, :n_vehicles) as gs;

-- expenses_per_vehicle despesas por veículo recém-criado, categorias sem os campos
-- exclusivos de COMBUSTIVEL (litros_ou_kwh/odometro) pra não esbarrar no
-- chk_expense_entry_combustivel_fields.
insert into core.expense_entry (id, tenant_id, vehicle_id, categoria, valor, data, descricao, fonte, created_at)
select
    gen_random_uuid(),
    :tenant_id,
    v.id,
    (array['MANUTENCAO','SEGURO','PEDAGIO','OUTRO'])[1 + ((v.seq + e.seq) % 4)],
    round((50 + ((v.seq * 7 + e.seq * 13) % 950))::numeric, 2),
    current_date - ((v.seq * 3 + e.seq * 11) % 180)::int,
    'Despesa de teste de carga',
    'MANUAL',
    now()
from (
    select id, row_number() over () as seq
    from core.vehicle
    where tenant_id = :tenant_id and plate like 'LT%'
) v
cross join generate_series(1, :expenses_per_vehicle) as e(seq);

select
    (select count(*) from core.vehicle where tenant_id = :tenant_id and plate like 'LT%') as veiculos_criados,
    (select count(*) from core.expense_entry ee join core.vehicle v on v.id = ee.vehicle_id
        where v.tenant_id = :tenant_id and v.plate like 'LT%') as despesas_criadas;
