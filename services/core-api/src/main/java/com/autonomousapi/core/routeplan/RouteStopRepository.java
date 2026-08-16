package com.autonomousapi.core.routeplan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

    List<RouteStop> findAllByRoutePlanIdOrderByOrdemSugeridaAsc(UUID routePlanId);

    Optional<RouteStop> findByIdAndRoutePlanId(UUID id, UUID routePlanId);
}
