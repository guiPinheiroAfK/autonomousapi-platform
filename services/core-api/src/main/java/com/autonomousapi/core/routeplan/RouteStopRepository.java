package com.autonomousapi.core.routeplan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

    List<RouteStop> findAllByRoutePlanIdOrderByOrdemSugeridaAsc(UUID routePlanId);

    /** Batch fetch para telas de listagem (evita N+1 — ver {@code RoutePlanService#toResponses}). */
    List<RouteStop> findAllByRoutePlanIdInOrderByOrdemSugeridaAsc(List<UUID> routePlanIds);

    Optional<RouteStop> findByIdAndRoutePlanId(UUID id, UUID routePlanId);

    /** Usado só por {@link RoutePlanService#update} (spec 11, gap "edição de rota já
     *  atribuída") — substitui a lista inteira de paradas, mesmo raciocínio de
     *  {@code create}. */
    void deleteAllByRoutePlanId(UUID routePlanId);
}
