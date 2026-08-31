package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatMessage;
import com.autonomousapi.core.chat.ChatMessageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code stillOnServer} (era {@code ainda_no_servidor} só internamente) — a UI usa isso pra
 * decidir se mostra editar/excluir/reagir: fora da janela de retenção do
 * {@code ChatCleanupJob}, o outro lado não teria como ver a mudança (V31). Mensagem apagada
 * ({@code deletedAt != null}) devolve {@code body: null} — o texto original fica no banco
 * pra auditoria, mas não sai mais pela API.
 */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderUserId,
        String body,
        Instant sentAt,
        Instant lidoEm,
        ChatMessageType messageType,
        UUID routePlanId,
        boolean stillOnServer,
        Instant editedAt,
        Instant deletedAt,
        UUID replyToMessageId,
        String replyToBody,
        UUID replyToSenderUserId,
        UUID forwardedFromMessageId,
        List<ChatReactionResponse> reactions) {

    public static ChatMessageResponse from(ChatMessage m, List<ChatReactionResponse> reactions) {
        boolean apagada = m.getDeletedAt() != null;
        return new ChatMessageResponse(
                m.getId(), m.getConversationId(), m.getSenderUserId(), apagada ? null : m.getBody(), m.getSentAt(),
                m.getLidoEm(), m.getMessageType(), m.getRoutePlanId(), m.isAindaNoServidor(), m.getEditedAt(),
                m.getDeletedAt(), m.getReplyToMessageId(), m.getReplyToBodySnapshot(), m.getReplyToSenderUserId(),
                m.getForwardedFromMessageId(), reactions);
    }

    public static ChatMessageResponse from(ChatMessage m) {
        return from(m, List.of());
    }
}
