package com.autonomousapi.core.routeplan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {

    /** Paginado (cleanup de performance) — único chamador é RoutePlanService#listForGestor. */
    Page<RoutePlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<RoutePlan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RoutePlan> findAllByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, List<RoutePlanStatus> status);
}
