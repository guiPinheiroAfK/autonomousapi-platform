package com.autonomousapi.core.driver.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code linkUrl} volta preenchido só nesta resposta de criação (nunca reconstruível
 *  depois — só o hash do token fica salvo) — o gestor pode copiar e mandar por fora
 *  (WhatsApp etc.) em vez de depender só da entrega do e-mail. */
public record DriverInviteResponse(UUID driverId, String email, Instant expiresAt, String linkUrl) {
}
