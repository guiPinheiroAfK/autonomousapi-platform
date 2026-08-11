package com.autonomousapi.core.vehicle.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento de custo de um veículo (combustível, manutenção, outro). Sem tenant_id
 * próprio: o isolamento por tenant vem de vehicle_id — a aplicação sempre resolve o
 * veículo escopado ao tenant antes de tocar em qualquer custo dele (ADR de escopo
 * seguido pelo VehicleService/DriverService).
 */
@Entity
@Table(name = "vehicle_cost_entry")
public class VehicleCostEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private VehicleCostCategory category;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDate occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehicleCostEntry() {
        // JPA
    }

    public VehicleCostEntry(UUID vehicleId, VehicleCostCategory category, BigDecimal amount,
            String description, LocalDate occurredAt) {
        this.id = UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public VehicleCostCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
