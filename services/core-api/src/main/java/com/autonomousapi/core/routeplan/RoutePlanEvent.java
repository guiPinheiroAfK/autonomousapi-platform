package com.autonomousapi.core.routeplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Um registro por transição relevante no ciclo de vida de um {@link RoutePlan} (ADR 0020).
 * Eventos discretos, nunca um campo calculado — permite reconstruir qualquer métrica depois
 * sem prever de antemão todas as que vão interessar. Schema {@code core}, gravado sempre na
 * mesma transação que a mudança de estado que ele registra (nunca uma chamada separada que
 * possa falhar independente do resto).
 */
@Entity
@Table(name = "route_plan_event")
public class RoutePlanEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "route_plan_id", nullable = false, updatable = false)
    private UUID routePlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private RoutePlanEventType tipo;

    @Column(name = "ator_user_id", updatable = false)
    private UUID atorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadado", updatable = false)
    private Map<String, Object> metadado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoutePlanEvent() {
        // JPA
    }

    public RoutePlanEvent(UUID routePlanId, RoutePlanEventType tipo, UUID atorUserId, Map<String, Object> metadado) {
        this.id = UUID.randomUUID();
        this.routePlanId = routePlanId;
        this.tipo = tipo;
        this.atorUserId = atorUserId;
        this.metadado = metadado == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadado);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoutePlanId() {
        return routePlanId;
    }

    public RoutePlanEventType getTipo() {
        return tipo;
    }

    public UUID getAtorUserId() {
        return atorUserId;
    }

    public Map<String, Object> getMetadado() {
        return metadado == null ? Map.of() : Map.copyOf(metadado);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
