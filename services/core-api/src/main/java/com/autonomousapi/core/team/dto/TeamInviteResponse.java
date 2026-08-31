package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.team.TeamInvite;
import com.autonomousapi.core.user.Role;
import java.time.Instant;
import java.util.UUID;

public record TeamInviteResponse(UUID id, String email, String nome, Role role, Instant expiresAt) {

    public static TeamInviteResponse from(TeamInvite i) {
        return new TeamInviteResponse(i.getId(), i.getEmail(), i.getNome(), i.getRole(), i.getExpiresAt());
    }
}
