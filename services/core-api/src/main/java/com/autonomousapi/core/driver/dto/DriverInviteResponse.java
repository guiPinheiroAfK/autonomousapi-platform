package com.autonomousapi.core.driver.dto;

import java.time.Instant;
import java.util.UUID;

/** Confirmação de convite enviado — o token cru nunca é devolvido, só vai no e-mail. */
public record DriverInviteResponse(UUID driverId, String email, Instant expiresAt) {
}
