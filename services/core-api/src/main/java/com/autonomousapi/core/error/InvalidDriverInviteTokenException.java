package com.autonomousapi.core.error;

/** Convite de motorista ausente, expirado ou já usado (ADR 0013). */
public class InvalidDriverInviteTokenException extends RuntimeException {

    public InvalidDriverInviteTokenException() {
        super("Convite inválido ou expirado.");
    }
}
