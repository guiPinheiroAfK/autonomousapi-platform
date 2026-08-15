package com.autonomousapi.core.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Designação motorista→veículo com histórico (ADR 0014). {@code endedAt} nulo = designação
 * ativa. Índices únicos parciais no banco garantem no máximo uma ativa por motorista e por
 * veículo — a invariante é regra de banco, não convenção de código.
 */
@Entity
@Table(name = "driver_vehicle_assignment")
public class DriverVehicleAssignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriverVehicleAssignment() {
        // JPA
    }

    public DriverVehicleAssignment(UUID tenantId, UUID driverId, UUID vehicleId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        Instant now = Instant.now();
        this.startedAt = now;
        this.createdAt = now;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public void end() {
        if (endedAt == null) {
            this.endedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
