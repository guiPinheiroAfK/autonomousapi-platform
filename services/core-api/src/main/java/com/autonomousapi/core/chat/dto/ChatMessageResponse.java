package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatMessage;
import com.autonomousapi.core.chat.ChatMessageType;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderUserId,
        String body,
        Instant sentAt,
        ChatMessageType messageType,
        UUID routePlanId) {

    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(
                m.getId(), m.getConversationId(), m.getSenderUserId(), m.getBody(), m.getSentAt(),
                m.getMessageType(), m.getRoutePlanId());
    }
}
