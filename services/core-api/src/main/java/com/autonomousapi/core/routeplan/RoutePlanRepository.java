package com.autonomousapi.core.routeplan;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, UUID> {

    /** Paginado (cleanup de performance) — único chamador é RoutePlanService#listForGestor. */
    Page<RoutePlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<RoutePlan> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * SELECT ... FOR UPDATE — usada por {@link RoutePlanService#completeStop} e
     * {@link RoutePlanService#assignDriver} pra serializar escritas concorrentes na mesma
     * rota (achado da revisão de código, 2026-08-25: sem lock, duas conclusões de parada
     * quase simultâneas podiam travar a rota em EM_ANDAMENTO pra sempre, e duas atribuições
     * concorrentes podiam sobrescrever uma à outra sem lançar RoutePlanAlreadyAssignedException).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoutePlan> findForUpdateById(UUID id);

    List<RoutePlan> findAllByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, List<RoutePlanStatus> status);
}
