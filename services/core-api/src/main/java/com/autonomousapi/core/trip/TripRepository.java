package com.autonomousapi.core.trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    List<Trip> findAllByTenantIdAndUserIdOrderByStartedAtDesc(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, TripStatus status);
}
