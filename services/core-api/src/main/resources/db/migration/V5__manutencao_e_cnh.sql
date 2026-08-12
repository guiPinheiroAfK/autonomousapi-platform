-- V5 — Alertas de manutenção/vencimento (spec 05, Fase 1: core-api).
-- Campos opcionais: o gestor pode não saber a data/km da próxima manutenção
-- no momento do cadastro. Sem valor => veículo/motorista não entra em nenhum
-- alerta (não é "vencido por padrão").

alter table vehicle add column proxima_manutencao_data date;
alter table vehicle add column proxima_manutencao_km integer;

alter table driver add column cnh_validade date;
