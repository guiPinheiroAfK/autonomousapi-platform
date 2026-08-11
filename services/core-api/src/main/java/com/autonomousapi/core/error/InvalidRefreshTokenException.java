package com.autonomousapi.core.error;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token inválido ou expirado.");
    }
}
