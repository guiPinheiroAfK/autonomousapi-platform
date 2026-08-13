package com.autonomousapi.core.vehicle.condition;

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

/** Sinistro/batida registrado no veículo (spec 06, item 2). */
@Entity
@Table(name = "vehicle_incident")
public class VehicleIncident {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", nullable = false, length = 20)
    private IncidentSeverity severidade;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "custo_reparo", precision = 12, scale = 2)
    private BigDecimal custoReparo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehicleIncident() {
        // JPA
    }

    public VehicleIncident(
            UUID vehicleId, LocalDate data, IncidentSeverity severidade, String descricao, BigDecimal custoReparo) {
        this.id = UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.data = data;
        this.severidade = severidade;
        this.descricao = descricao;
        this.custoReparo = custoReparo;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public LocalDate getData() {
        return data;
    }

    public IncidentSeverity getSeveridade() {
        return severidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getCustoReparo() {
        return custoReparo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
