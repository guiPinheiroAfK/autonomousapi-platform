package com.autonomousapi.core.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Gestores/admins do tenant — usado por BudgetAlertJob pra notificar quem gerencia a frota. */
    List<User> findAllByTenantIdAndRoleIn(UUID tenantId, List<Role> roles);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
}
