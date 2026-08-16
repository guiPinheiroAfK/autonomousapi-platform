package com.autonomousapi.core.driver.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Componente automático de avaliação de motorista (spec 06, item 3), calculado a partir
 * do dado de condução de uma viagem (schema {@code geo}, via {@code GeoApiClient}). Um
 * lançamento por (viagem, componente) — {@link DriverAutoRatingJob} nunca reprocessa a
 * mesma viagem duas vezes (ver {@code trip.rating_processed_at}).
 *
 * {@code score} vai de 0.00 (pior) a 1.00 (sem ocorrência) — escala própria do
 * componente automático, diferente da nota manual (1-5). O blend das duas fica em
 * {@link DriverRatingService}.
 */
@Entity
@Table(name = "driver_rating_auto")
public class DriverRatingAuto {

    public static final String COMPONENTE_FRENAGEM_BRUSCA = "FRENAGEM_BRUSCA";
    public static final String COMPONENTE_EXCESSO_VELOCIDADE = "EXCESSO_VELOCIDADE";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "componente", nullable = false, length = 30, updatable = false)
    private String componente;

    @Column(name = "score", nullable = false, precision = 3, scale = 2, updatable = false)
    private BigDecimal score;

    @Column(name = "observado_em", nullable = false, updatable = false)
    private Instant observadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriverRatingAuto() {
        // JPA
    }

    public DriverRatingAuto(UUID driverId, UUID tripId, String componente, BigDecimal score, Instant observadoEm) {
        this.id = UUID.randomUUID();
        this.driverId = driverId;
        this.tripId = tripId;
        this.componente = componente;
        this.score = score;
        this.observadoEm = observadoEm;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getComponente() {
        return componente;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Instant getObservadoEm() {
        return observadoEm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
