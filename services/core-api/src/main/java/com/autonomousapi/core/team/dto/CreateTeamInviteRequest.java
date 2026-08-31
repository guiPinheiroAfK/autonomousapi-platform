package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTeamInviteRequest(
        @NotBlank @Email String email,
        @NotBlank String nome,
        @NotNull Role role) {
}
