package com.autonomousapi.core.auth.dto;

/** V34 — resultado de {@code POST /v1/auth/login}: ou {@code tokens} (caso comum, uma só
 *  conta pra esse e-mail+senha), ou {@code tenantChoice} (a senha bate em mais de uma conta —
 *  o cliente completa com {@code POST /v1/auth/select-tenant}). Nunca os dois preenchidos. */
public record LoginResult(TokenResponse tokens, TenantChoiceResponse tenantChoice) {

    public static LoginResult tokens(TokenResponse tokens) {
        return new LoginResult(tokens, null);
    }

    public static LoginResult chooseTenant(TenantChoiceResponse tenantChoice) {
        return new LoginResult(null, tenantChoice);
    }
}
