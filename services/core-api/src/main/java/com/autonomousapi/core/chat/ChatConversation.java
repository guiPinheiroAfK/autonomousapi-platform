package com.autonomousapi.core.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Conversa 1:1 gestor↔motorista (ADR 0015). Uma por par (gestor, motorista). */
@Entity
@Table(name = "chat_conversation")
public class ChatConversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "gestor_user_id", nullable = false, updatable = false)
    private UUID gestorUserId;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatConversation() {
        // JPA
    }

    public ChatConversation(UUID tenantId, UUID gestorUserId, UUID driverId, UUID vehicleId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.gestorUserId = gestorUserId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.createdAt = Instant.now();
    }

    /** True se o usuário (gestor ou motorista) faz parte desta conversa. */
    public boolean hasParticipant(UUID userId, UUID participantDriverId) {
        return userId.equals(gestorUserId) || (participantDriverId != null && participantDriverId.equals(driverId));
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGestorUserId() {
        return gestorUserId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
