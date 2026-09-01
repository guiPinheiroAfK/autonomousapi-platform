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

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "reply_to_body_snapshot", length = 200)
    private String replyToBodySnapshot;

    @Column(name = "reply_to_sender_user_id")
    private UUID replyToSenderUserId;

    @Column(name = "forwarded_from_message_id")
    private UUID forwardedFromMessageId;

    protected ChatMessage() {
        // JPA
    }

    public ChatMessage(UUID conversationId, UUID senderUserId, String body) {
        this(conversationId, senderUserId, body, ChatMessageType.TEXTO, null);
    }

    /** Mensagem estruturada (ex. ATRIBUICAO_ROTA) — {@code body} é sempre o texto de
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

    /** Resposta a outra mensagem — o retrato (texto + autor) é copiado aqui no momento do
     *  envio, não uma referência viva (a original pode sair da janela de retenção depois). */
    public void responderA(UUID originalId, String originalBodySnapshot, UUID originalSenderUserId) {
        this.replyToMessageId = originalId;
        this.replyToBodySnapshot = originalBodySnapshot;
        this.replyToSenderUserId = originalSenderUserId;
    }

    public void marcarComoEncaminhada(UUID originalMessageId) {
        this.forwardedFromMessageId = originalMessageId;
    }

    /** Removida do servidor pelo job de limpeza — o dispositivo do gestor já sincronizou. */
    public void removerDoServidor() {
        this.aindaNoServidor = false;
    }

    /** Marca como lida pelo destinatário — idempotente, não sobrescreve o timestamp original. */
    public void marcarComoLida() {
        if (this.lidoEm == null) {
            this.lidoEm = Instant.now();
        }
    }

    /** Só {@code TEXTO}, só enquanto {@code aindaNoServidor} (checado pelo service antes de
     *  chamar isto) — editar mensagem estruturada ou fora da janela de retenção não faz
     *  sentido (o outro lado não teria como ver a mudança). */
    public void editar(String novoBody) {
        this.body = novoBody;
        this.editedAt = Instant.now();
    }

    /** Soft delete — mantém a linha (mesma filosofia de {@link #removerDoServidor}), só
     *  marca {@code deletedAt}; a resposta da API esconde o {@code body} original a partir
     *  daqui, mas o texto continua no banco. */
    public void apagar() {
        this.deletedAt = Instant.now();
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

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getReplyToMessageId() {
        return replyToMessageId;
    }

    public String getReplyToBodySnapshot() {
        return replyToBodySnapshot;
    }

    public UUID getReplyToSenderUserId() {
        return replyToSenderUserId;
    }

    public UUID getForwardedFromMessageId() {
        return forwardedFromMessageId;
    }
}
