package com.autonomousapi.core.routeplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Rota com múltiplas paradas de coleta/entrega (spec 02, "Roteamento com múltiplos
 * pontos"). {@code status} nunca é setado de fora de {@link RoutePlanService#completeStop}
 * — é sempre derivado do estado das {@link RouteStop} (ver migration V20).
 */
@Entity
@Table(name = "route_plan")
public class RoutePlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "gestor_user_id", nullable = false, updatable = false)
    private UUID gestorUserId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutePlanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoutePlan() {
        // JPA
    }

    public RoutePlan(UUID tenantId, UUID gestorUserId, UUID driverId, UUID vehicleId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.gestorUserId = gestorUserId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.status = RoutePlanStatus.PLANEJADA;
        this.createdAt = Instant.now();
    }

    public void designarMotorista(UUID driverId) {
        this.driverId = driverId;
    }

    public void avancarStatus(RoutePlanStatus novo) {
        this.status = novo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGestorUserId() {
        return gestorUserId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public RoutePlanStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
