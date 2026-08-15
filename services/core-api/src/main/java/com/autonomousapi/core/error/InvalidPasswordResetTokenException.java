package com.autonomousapi.core.error;

/** Token de recuperação de senha ausente, expirado ou já usado. */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Link de redefinição inválido ou expirado.");
    }
}
