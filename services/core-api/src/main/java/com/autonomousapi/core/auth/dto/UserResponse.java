package com.autonomousapi.core.auth.dto;

import com.autonomousapi.core.user.User;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        UUID tenantId) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getTenantId());
    }
}
