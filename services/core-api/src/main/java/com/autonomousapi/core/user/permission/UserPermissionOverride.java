package com.autonomousapi.core.user.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Diferença entre a permissão efetiva de um usuário e o padrão do papel dele (ADR 0025).
 *
 * <p>Guarda só o que difere, nunca o conjunto inteiro: quem nunca teve permissão ajustada
 * não tem linha nenhuma aqui, e mudar o papel da pessoa continua funcionando como antes
 * (o padrão novo passa a valer sozinho, sem precisar reescrever nada). {@code allowed}
 * existe nos dois sentidos porque um ajuste tanto concede algo fora do padrão
 * ({@code true}) quanto tira algo que o padrão dava ({@code false}).
 */
@Entity
@Table(name = "user_permission_override")
public class UserPermissionOverride {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40, updatable = false)
    private Permission permission;

    @Column(name = "allowed", nullable = false)
    private boolean allowed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserPermissionOverride() {
        // JPA
    }

    public UserPermissionOverride(UUID userId, Permission permission, boolean allowed) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.permission = permission;
        this.allowed = allowed;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Permission getPermission() {
        return permission;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
