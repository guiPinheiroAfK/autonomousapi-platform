package com.autonomousapi.core.workorder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    /** Sem paginação — usado só por {@code maintenanceSummary}, que precisa do histórico
     *  completo dos últimos 12 meses pra montar o relatório (não é uma tela de listagem). */
    List<WorkOrder> findAllByTenantIdOrderByDataAberturaDesc(UUID tenantId);

    /** Paginado (cleanup de performance) — a tela de listagem (`GET /v1/work-orders`) não
     *  precisa do histórico inteiro do tenant de uma vez. */
    Page<WorkOrder> findAllByTenantIdOrderByDataAberturaDesc(UUID tenantId, Pageable pageable);

    Page<WorkOrder> findAllByTenantIdAndVehicleIdOrderByDataAberturaDesc(UUID tenantId, UUID vehicleId, Pageable pageable);

    Optional<WorkOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndDataAberturaBetween(UUID tenantId, java.time.LocalDate start, java.time.LocalDate end);
}
