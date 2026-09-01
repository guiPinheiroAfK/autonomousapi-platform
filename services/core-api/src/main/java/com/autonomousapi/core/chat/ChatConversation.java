package com.autonomousapi.core.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Conversa 1:1 — dois tipos (ADR 0015 + V33, chat em equipe):
 *
 * <ul>
 *   <li>{@link ChatConversationKind#GESTOR_MOTORISTA} (default, comportamento original): uma
 *       por par (gestor, motorista) — {@code gestorUserId}/{@code driverId} exatamente como
 *       antes.
 *   <li>{@link ChatConversationKind#EQUIPE}: qualquer membro da equipe (Gestor/Despachante/
 *       Visualizador) com qualquer outro do mesmo tenant — {@code driverId}/{@code
 *       vehicleId} ficam nulos; o segundo participante vai em {@code participantBUserId}.
 * </ul>
 *
 * {@code gestorUserId} passa a significar "participante A" pra qualquer kind — o nome da
 * coluna/getter não mudou pra não obrigar tocar em todo lugar que já lê
 * {@link #getGestorUserId()} (a maioria do código só existe pro caso GESTOR_MOTORISTA, onde
 * o nome ainda é literal). Pra EQUIPE, sempre o par ordenado (menor UUID em
 * {@code gestorUserId}, maior em {@code participantBUserId}) — ver
 * {@link #novaConversaEquipe}.
 */
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

    @Column(name = "driver_id", updatable = false)
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private ChatConversationKind kind = ChatConversationKind.GESTOR_MOTORISTA;

    @Column(name = "participant_b_user_id", updatable = false)
    private UUID participantBUserId;

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
        this.kind = ChatConversationKind.GESTOR_MOTORISTA;
        this.createdAt = Instant.now();
    }

    /** Par sempre ordenado (menor UUID primeiro) — garante uma linha só por par, não duas,
     *  não importa quem inicia a conversa. */
    public static ChatConversation novaConversaEquipe(UUID tenantId, UUID userA, UUID userB) {
        UUID menor = userA.compareTo(userB) <= 0 ? userA : userB;
        UUID maior = userA.compareTo(userB) <= 0 ? userB : userA;
        ChatConversation c = new ChatConversation();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.gestorUserId = menor;
        c.participantBUserId = maior;
        c.kind = ChatConversationKind.EQUIPE;
        c.createdAt = Instant.now();
        return c;
    }

    /** True se o usuário faz parte desta conversa. Pra GESTOR_MOTORISTA, {@code
     *  participantDriverId} é o id do {@code Driver} do token (resolvido pelo chamador antes
     *  — motorista não navega por app_user_id aqui); pra EQUIPE, não é usado. */
    public boolean hasParticipant(UUID userId, UUID participantDriverId) {
        if (kind == ChatConversationKind.EQUIPE) {
            return userId.equals(gestorUserId) || userId.equals(participantBUserId);
        }
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

    public ChatConversationKind getKind() {
        return kind;
    }

    public UUID getParticipantBUserId() {
        return participantBUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
