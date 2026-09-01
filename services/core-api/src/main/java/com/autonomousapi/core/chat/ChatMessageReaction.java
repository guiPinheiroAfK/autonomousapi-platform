package com.autonomousapi.core.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Reação de uma pessoa a uma {@link ChatMessage} — uma por pessoa por mensagem (constraint
 * única no banco, V31). Tocar de nova no mesmo emoji remove (delete); tocar em outro
 * substitui (upsert no {@link ChatService}) — igual WhatsApp.
 */
@Entity
@Table(name = "chat_message_reaction")
public class ChatMessageReaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "emoji", nullable = false, length = 8)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessageReaction() {
        // JPA
    }

    public ChatMessageReaction(UUID messageId, UUID userId, String emoji) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmoji() {
        return emoji;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
