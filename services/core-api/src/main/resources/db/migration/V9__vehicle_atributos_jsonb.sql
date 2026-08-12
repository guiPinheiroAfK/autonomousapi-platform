-- V9 — Atributos variáveis do veículo em jsonb (ADR 0008).
--
-- O que é filtrado, ordenado ou indexado CONTINUA sendo coluna de verdade (placa, status,
-- odômetro, próxima manutenção). Só entra aqui o que varia por tipo de veículo e cresce com
-- o produto: autonomia e tipo de conector num elétrico, cilindrada numa moto, valor FIPE,
-- histórico de sinistro. Sem isso, cada atributo novo viraria uma migration e uma coluna
-- nula para todo mundo que não a usa.

alter table vehicle
    add column atributos jsonb not null default '{}'::jsonb;

-- GIN com jsonb_path_ops: menor e mais rápido que o padrão para consulta de contenção
-- (`atributos @> '{"combustivel":"eletrico"}'`), que é o caso de uso previsto. Não serve
-- para busca por existência de chave (`?`), que não precisamos.
create index idx_vehicle_atributos on vehicle using gin (atributos jsonb_path_ops);
