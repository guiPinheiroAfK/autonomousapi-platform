package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.team.TeamInvite;
import com.autonomousapi.core.user.Role;
import java.time.Instant;
import java.util.UUID;

/** {@code linkUrl} só vem preenchido na resposta de criação do convite (nunca reconstruível
 *  depois — só o hash do token fica salvo) — o gestor pode copiar e mandar por fora
 *  (WhatsApp etc.) em vez de depender só da entrega do e-mail. */
public record TeamInviteResponse(UUID id, String email, String nome, Role role, Instant expiresAt, String linkUrl) {

    public static TeamInviteResponse from(TeamInvite i) {
        return from(i, null);
    }

    public static TeamInviteResponse from(TeamInvite i, String linkUrl) {
        return new TeamInviteResponse(i.getId(), i.getEmail(), i.getNome(), i.getRole(), i.getExpiresAt(), linkUrl);
    }
}
