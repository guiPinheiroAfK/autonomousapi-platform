package com.autonomousapi.core.routeplan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {

    List<RoutePlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<RoutePlan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RoutePlan> findAllByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, List<RoutePlanStatus> status);
}
