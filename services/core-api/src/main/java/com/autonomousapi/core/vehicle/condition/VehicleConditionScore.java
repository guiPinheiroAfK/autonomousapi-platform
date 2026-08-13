package com.autonomousapi.core.vehicle.condition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Score de condição do veículo (spec 06, item 2) — agregado, nunca calculado on-the-fly
 * na leitura. Fórmula v1 é heurística simples e versionada (ver VehicleConditionService),
 * a calibrar com dado real conforme o spec já antecipa.
 */
@Entity
@Table(name = "vehicle_condition_score")
public class VehicleConditionScore {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false, unique = true)
    private UUID vehicleId;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VehicleConditionScore() {
        // JPA
    }

    public VehicleConditionScore(UUID vehicleId, BigDecimal score, String algorithmVersion) {
        this.id = UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.score = score;
        this.algorithmVersion = algorithmVersion;
        this.updatedAt = Instant.now();
    }

    public void atualizar(BigDecimal score, String algorithmVersion) {
        this.score = score;
        this.algorithmVersion = algorithmVersion;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
