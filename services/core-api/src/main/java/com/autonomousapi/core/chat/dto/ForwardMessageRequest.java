package com.autonomousapi.core.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ForwardMessageRequest(@NotNull UUID targetConversationId) {
}
