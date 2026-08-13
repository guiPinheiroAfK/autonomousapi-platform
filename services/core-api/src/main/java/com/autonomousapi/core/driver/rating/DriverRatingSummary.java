package com.autonomousapi.core.driver.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado de {@link DriverRatingManual} por motorista (spec 06). Nunca calculado
 * on-the-fly na leitura — recomputado por DriverRatingService a cada avaliação nova.
 * Quando a avaliação automática (spec 06, Fase 3+) existir, este agregado passa a
 * combinar as duas fontes; por ora é só a média manual.
 */
@Entity
@Table(name = "driver_rating_summary")
public class DriverRatingSummary {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false, updatable = false, unique = true)
    private UUID driverId;

    @Column(name = "nota_media", nullable = false, precision = 3, scale = 2)
    private BigDecimal notaMedia;

    @Column(name = "total_avaliacoes", nullable = false)
    private int totalAvaliacoes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DriverRatingSummary() {
        // JPA
    }

    public DriverRatingSummary(UUID driverId, BigDecimal notaMedia, int totalAvaliacoes) {
        this.id = UUID.randomUUID();
        this.driverId = driverId;
        this.notaMedia = notaMedia;
        this.totalAvaliacoes = totalAvaliacoes;
        this.updatedAt = Instant.now();
    }

    public void atualizar(BigDecimal notaMedia, int totalAvaliacoes) {
        this.notaMedia = notaMedia;
        this.totalAvaliacoes = totalAvaliacoes;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public BigDecimal getNotaMedia() {
        return notaMedia;
    }

    public int getTotalAvaliacoes() {
        return totalAvaliacoes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
