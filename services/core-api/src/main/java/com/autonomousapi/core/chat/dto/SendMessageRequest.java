package com.autonomousapi.core.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code replyToMessageId} opcional — quando presente, {@link com.autonomousapi.core.chat.ChatService#sendMessage}
 *  copia um retrato (texto + autor) da mensagem original pra dentro da nova, ver ADR do V31. */
public record SendMessageRequest(@NotBlank @Size(max = 2000) String body, UUID replyToMessageId) {
}
