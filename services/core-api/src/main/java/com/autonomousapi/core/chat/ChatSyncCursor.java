package com.autonomousapi.core.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Até onde um dispositivo do gestor já sincronizou o histórico local (ADR 0015). Cursor por
 * dispositivo, não por conversa — o job de limpeza só remove o que TODOS os devices do
 * gestor já confirmaram.
 */
@Entity
@Table(name = "chat_sync_cursor")
public class ChatSyncCursor {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gestor_user_id", nullable = false, updatable = false)
    private UUID gestorUserId;

    @Column(name = "device_id", nullable = false, length = 200)
    private String deviceId;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatSyncCursor() {
        // JPA
    }

    public ChatSyncCursor(UUID gestorUserId, String deviceId, Instant syncedAt) {
        this.id = UUID.randomUUID();
        this.gestorUserId = gestorUserId;
        this.deviceId = deviceId;
        this.syncedAt = syncedAt;
        this.updatedAt = Instant.now();
    }

    /** Upsert: o mesmo device confirma um ponto de sync mais recente. */
    public void avancar(Instant syncedAt) {
        this.syncedAt = syncedAt;
        this.updatedAt = Instant.now();
    }

    public UUID getGestorUserId() {
        return gestorUserId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
