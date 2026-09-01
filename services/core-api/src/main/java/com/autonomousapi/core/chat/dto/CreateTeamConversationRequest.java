package com.autonomousapi.core.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** V33, chat em equipe. */
public record CreateTeamConversationRequest(@NotNull UUID otherUserId) {
}
