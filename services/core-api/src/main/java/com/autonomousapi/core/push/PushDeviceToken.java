package com.autonomousapi.core.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Device token de push registrado por um usuário (ADR 0016). Token é a chave natural. */
@Entity
@Table(name = "push_device_token")
public class PushDeviceToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "plataforma", nullable = false, length = 20)
    private String plataforma;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushDeviceToken() {
        // JPA
    }

    public PushDeviceToken(UUID userId, String token, String plataforma) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.token = token;
        this.plataforma = plataforma;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Upsert: mesmo token, dono pode ter mudado (ex. outro motorista logou no aparelho). */
    public void reassign(UUID userId, String plataforma) {
        this.userId = userId;
        this.plataforma = plataforma;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
