package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeTeamRoleRequest(@NotNull Role role) {
}
