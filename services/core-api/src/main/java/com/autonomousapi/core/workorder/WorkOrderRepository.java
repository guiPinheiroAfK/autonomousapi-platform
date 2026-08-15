package com.autonomousapi.core.workorder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    List<WorkOrder> findAllByTenantIdOrderByDataAberturaDesc(UUID tenantId);

    List<WorkOrder> findAllByTenantIdAndVehicleIdOrderByDataAberturaDesc(UUID tenantId, UUID vehicleId);

    Optional<WorkOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndDataAberturaBetween(UUID tenantId, java.time.LocalDate start, java.time.LocalDate end);
}
