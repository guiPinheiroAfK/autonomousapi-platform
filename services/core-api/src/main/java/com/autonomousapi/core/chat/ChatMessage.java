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
 * Mensagem de uma {@link ChatConversation} (ADR 0015). {@code aindaNoServidor} controla o
 * que já foi removido pelo job de limpeza — soft delete, a linha continua existindo.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "lido_em")
    private Instant lidoEm;

    @Column(name = "ainda_no_servidor", nullable = false)
    private boolean aindaNoServidor;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(name = "route_plan_id")
    private UUID routePlanId;

    protected ChatMessage() {
        // JPA
    }

    public ChatMessage(UUID conversationId, UUID senderUserId, String body) {
        this(conversationId, senderUserId, body, ChatMessageType.TEXT, null);
    }

    /** Mensagem estruturada (ex. ROUTE_ASSIGNMENT) — {@code body} é sempre o texto de
     *  fallback pra quem não interpreta {@code messageType}. */
    public ChatMessage(UUID conversationId, UUID senderUserId, String body, ChatMessageType messageType, UUID routePlanId) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.senderUserId = senderUserId;
        this.body = body;
        this.sentAt = Instant.now();
        this.aindaNoServidor = true;
        this.messageType = messageType;
        this.routePlanId = routePlanId;
    }

    /** Removida do servidor pelo job de limpeza — o dispositivo do gestor já sincronizou. */
    public void removerDoServidor() {
        this.aindaNoServidor = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getLidoEm() {
        return lidoEm;
    }

    public boolean isAindaNoServidor() {
        return aindaNoServidor;
    }

    public ChatMessageType getMessageType() {
        return messageType;
    }

    public UUID getRoutePlanId() {
        return routePlanId;
    }
}
