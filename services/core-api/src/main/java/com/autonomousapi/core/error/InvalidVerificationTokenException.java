package com.autonomousapi.core.error;

/** Token de confirmação de e-mail ausente, expirado ou já usado. */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("Link de confirmação inválido ou expirado.");
    }
}
