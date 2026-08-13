package com.autonomousapi.core.driver.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação manual de um motorista pelo gestor (spec 06, item 3). Regra de acesso
 * NÃO-NEGOCIÁVEL do spec: nunca exposta ao próprio motorista nem a terceiros — só ao
 * gestor/tenant que contratou. Reforçada em DriverRatingController (@PreAuthorize), não
 * é opção de UI.
 */
@Entity
@Table(name = "driver_rating_manual")
public class DriverRatingManual {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "gestor_user_id", nullable = false, updatable = false)
    private UUID gestorUserId;

    @Column(name = "nota", nullable = false)
    private short nota;

    @Column(name = "comentario", length = 500)
    private String comentario;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriverRatingManual() {
        // JPA
    }

    public DriverRatingManual(UUID driverId, UUID gestorUserId, short nota, String comentario) {
        this.id = UUID.randomUUID();
        this.driverId = driverId;
        this.gestorUserId = gestorUserId;
        this.nota = nota;
        this.comentario = comentario;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getGestorUserId() {
        return gestorUserId;
    }

    public short getNota() {
        return nota;
    }

    public String getComentario() {
        return comentario;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
