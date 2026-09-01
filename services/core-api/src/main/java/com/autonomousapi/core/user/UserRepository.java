package com.autonomousapi.core.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Global (V34: e-mail não é mais único globalmente) — ainda serve pra
     *  googleAuth/resendVerification/forgotPassword, que pegam "a primeira conta habilitada"
     *  quando o e-mail tem mais de uma (esses três fluxos não ganham escolha de tenant nesta
     *  entrega, só não podem quebrar quando existir mais de uma linha). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** V34, login com múltiplas contas — todas as linhas (potencialmente em tenants
     *  diferentes) pra um e-mail. */
    List<User> findAllByEmail(String email);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    /** Gestores/admins do tenant — usado por BudgetAlertJob pra notificar quem gerencia a frota. */
    List<User> findAllByTenantIdAndRoleIn(UUID tenantId, List<Role> roles);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
}
