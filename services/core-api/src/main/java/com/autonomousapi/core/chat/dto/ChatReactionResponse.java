package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatMessageReaction;
import java.util.UUID;

public record ChatReactionResponse(String emoji, UUID userId) {

    public static ChatReactionResponse from(ChatMessageReaction r) {
        return new ChatReactionResponse(r.getEmoji(), r.getUserId());
    }
}
