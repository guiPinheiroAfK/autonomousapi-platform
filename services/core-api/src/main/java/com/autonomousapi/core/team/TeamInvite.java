package com.autonomousapi.core.team;

import com.autonomousapi.core.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Convite de acesso de equipe (spec 15) — mesmo desenho de token de {@code driver_invite}
 * (ADR 0013), mas sem vínculo com um registro operacional pré-existente: um novo membro
 * de equipe não tem "driver" prévio, então email/nome vêm no próprio convite.
 */
@Entity
@Table(name = "team_invite")
public class TeamInvite {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 255, updatable = false)
    private String email;

    @Column(name = "nome", nullable = false, length = 200, updatable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20, updatable = false)
    private Role role;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    private UUID invitedByUserId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamInvite() {
        // JPA
    }

    public TeamInvite(
            UUID tenantId, String email, String nome, Role role, UUID invitedByUserId,
            String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email;
        this.nome = nome;
        this.role = role;
        this.invitedByUserId = invitedByUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public Role getRole() {
        return role;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
