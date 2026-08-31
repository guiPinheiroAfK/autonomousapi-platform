package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(UUID id, String email, Role role, boolean enabled, Instant createdAt) {

    public static TeamMemberResponse from(User u) {
        return new TeamMemberResponse(u.getId(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
