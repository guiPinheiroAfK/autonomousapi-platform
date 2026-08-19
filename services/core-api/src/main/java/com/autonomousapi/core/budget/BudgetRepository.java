package com.autonomousapi.core.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Budget> findByIdAndTenantId(UUID id, UUID tenantId);
}
