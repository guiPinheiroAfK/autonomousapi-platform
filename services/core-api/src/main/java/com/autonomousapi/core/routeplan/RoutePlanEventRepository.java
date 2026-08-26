package com.autonomousapi.core.routeplan;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePlanEventRepository extends JpaRepository<RoutePlanEvent, UUID> {
}
