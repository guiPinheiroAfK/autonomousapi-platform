-- V25 — Índices em caminhos de consulta descobertos na auditoria de performance (cleanup).
-- Nenhuma mudança de schema: só cobre queries que hoje fazem sequential scan.

-- AlertPushJob roda diariamente e varre TODA a base via findAllWithManutencaoAgendada()
-- (cross-tenant de propósito, ADR 0016). Índice parcial: só as linhas com alguma das duas
-- datas/km preenchidos importam para essa query (a maioria dos veículos não tem, ver V5).
create index idx_vehicle_manutencao_agendada on vehicle (proxima_manutencao_data, proxima_manutencao_km)
    where proxima_manutencao_data is not null or proxima_manutencao_km is not null;

-- Mesmo job, para CNH — findAllByCnhValidadeIsNotNullAndAppUserIdIsNotNull() varre toda a
-- base de motoristas diariamente.
create index idx_driver_cnh_validade on driver (cnh_validade) where cnh_validade is not null;

-- TripService.list filtra por tenant_id + user_id juntos e ordena por started_at; os
-- índices de V7 cobrem cada coluna isolada, mas não a combinação nem a ordenação.
create index idx_trip_tenant_user_started on trip (tenant_id, user_id, started_at);
