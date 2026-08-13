package com.autonomousapi.core.vehicle.marketvalue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Valor de mercado (FIPE) do veículo (spec 06, item 2) — cache do último valor
 * consultado, nunca calculado/chamado em tempo real na tela.
 *
 * Nesta rodada, o lançamento é manual pelo gestor (sem chave/matching de catálogo FIPE
 * ainda): {@code codigoFipe} fica opcional, para quando a integração automática existir
 * sem precisar de migration nova.
 */
@Entity
@Table(name = "vehicle_market_value")
public class VehicleMarketValue {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "valor_fipe", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorFipe;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Column(name = "codigo_fipe", length = 20)
    private String codigoFipe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehicleMarketValue() {
        // JPA
    }

    public VehicleMarketValue(
            UUID vehicleId, BigDecimal valorFipe, LocalDate dataReferencia, String codigoFipe) {
        this.id = UUID.randomUUID();
        this.vehicleId = vehicleId;
        this.valorFipe = valorFipe;
        this.dataReferencia = dataReferencia;
        this.codigoFipe = codigoFipe;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public BigDecimal getValorFipe() {
        return valorFipe;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public String getCodigoFipe() {
        return codigoFipe;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
