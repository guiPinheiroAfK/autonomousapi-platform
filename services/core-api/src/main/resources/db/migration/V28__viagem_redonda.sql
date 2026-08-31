-- V28 — Viagem redonda (spec 13): vínculo leve entre pernas de ida/volta.
-- Não é uma entidade nova — viagem_id é só uma chave de agrupamento compartilhada por
-- duas (ou mais, no futuro) linhas de route_plan. UUID solto em vez de auto-referência
-- pra permitir mais de duas pernas mais adiante sem mudar o schema (ver spec 13).

alter table route_plan add column viagem_id uuid;

-- Parcial: a maioria das rotas não é ida/volta — não vale indexar a tabela inteira por
-- uma coluna quase sempre nula.
create index idx_route_plan_viagem on route_plan (viagem_id) where viagem_id is not null;
