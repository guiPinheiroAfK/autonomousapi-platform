package com.autonomousapi.core.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Sessão de viagem do motorista (spec 03) — pings de GPS em si vivem no schema geo. */
@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Trip() {
        // JPA
    }

    public Trip(UUID tenantId, UUID userId, UUID vehicleId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.status = TripStatus.EM_ANDAMENTO;
        this.startedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void finish() {
        this.status = TripStatus.FINALIZADA;
        this.endedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
