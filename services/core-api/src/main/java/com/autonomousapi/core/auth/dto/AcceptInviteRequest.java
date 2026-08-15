package com.autonomousapi.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Aceite do convite de motorista: o token do e-mail + a senha escolhida (ADR 0013). */
public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
