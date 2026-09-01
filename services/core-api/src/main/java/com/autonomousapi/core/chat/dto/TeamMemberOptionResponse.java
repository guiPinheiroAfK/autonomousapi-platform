package com.autonomousapi.core.chat.dto;

import java.util.UUID;

/** V33, chat em equipe — opção pro seletor de "iniciar conversa com". */
public record TeamMemberOptionResponse(UUID userId, String email, String role) {
}
