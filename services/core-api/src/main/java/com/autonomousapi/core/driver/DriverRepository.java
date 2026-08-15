package com.autonomousapi.core.driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Driver> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCnh(UUID tenantId, String cnh);

    boolean existsByTenantIdAndCnhAndIdNot(UUID tenantId, String cnh, UUID id);

    List<Driver> findAllByTenantIdAndCnhValidadeIsNotNull(UUID tenantId);

    /** Resolve o driver a partir do login vinculado (ADR 0013) — base do CurrentDriverResolver. */
    Optional<Driver> findByAppUserId(UUID appUserId);
}
