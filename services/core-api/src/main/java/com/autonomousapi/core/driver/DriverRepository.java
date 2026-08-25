package com.autonomousapi.core.driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    /** Paginado (cleanup de performance) — único chamador é DriverService#list. */
    Page<Driver> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Driver> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCnh(UUID tenantId, String cnh);

    boolean existsByTenantIdAndCnhAndIdNot(UUID tenantId, String cnh, UUID id);

    List<Driver> findAllByTenantIdAndCnhValidadeIsNotNull(UUID tenantId);

    /** Resolve o driver a partir do login vinculado (ADR 0013) — base do CurrentDriverResolver. */
    Optional<Driver> findByAppUserId(UUID appUserId);

    /**
     * Cross-tenant de propósito: usado só pelo job diário de push (ADR 0016), que roda uma
     * vez para toda a base — só motoristas com login (sem login não tem pra onde mandar push).
     */
    List<Driver> findAllByCnhValidadeIsNotNullAndAppUserIdIsNotNull();
}
