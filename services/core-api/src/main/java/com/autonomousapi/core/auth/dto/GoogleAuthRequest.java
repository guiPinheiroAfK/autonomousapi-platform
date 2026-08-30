package com.autonomousapi.core.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** ID token que o Google Identity Services devolve pro frontend após o usuário escolher a
 *  conta — nunca um código de autorização nem client secret, o fluxo é todo client-side. */
public record GoogleAuthRequest(@NotBlank String idToken) {
}
