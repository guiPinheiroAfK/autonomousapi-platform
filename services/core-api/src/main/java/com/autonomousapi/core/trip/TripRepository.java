package com.autonomousapi.core.trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    /**
     * Paginado (cleanup de performance) — a lista de viagens de um motorista cresce sem
     * limite ao longo do tempo. A viagem em andamento, se houver, sempre aparece na
     * primeira página: como o {@code started_at} dela é o mais recente por definição,
     * a ordenação desc garante que ela nunca "cai" pra uma página seguinte.
     */
    Page<Trip> findAllByTenantIdAndUserIdOrderByStartedAtDesc(UUID tenantId, UUID userId, Pageable pageable);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, TripStatus status);

    /**
     * Cross-tenant de propósito: usado só pelo {@code DriverAutoRatingJob} (spec 06, item
     * 3), que precisa varrer toda a base numa passada, igual aos outros jobs diários.
     */
    List<Trip> findAllByStatusAndRatingProcessedAtIsNull(TripStatus status);
}
