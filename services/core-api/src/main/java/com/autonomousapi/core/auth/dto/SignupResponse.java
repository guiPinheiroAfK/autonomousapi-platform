package com.autonomousapi.core.auth.dto;

/**
 * Signup não devolve tokens mais (ADR 0011) — a conta nasce desabilitada, então não
 * haveria nada de útil pra fazer com um token de acesso ainda.
 */
public record SignupResponse(String email, String message) {

    public static SignupResponse pendingVerification(String email) {
        return new SignupResponse(email, "Enviamos um link de confirmação para o seu e-mail.");
    }
}
