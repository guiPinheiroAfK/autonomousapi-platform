package com.autonomousapi.core.error;

/** Convite de equipe ausente, expirado ou já usado (spec 15). */
public class InvalidTeamInviteTokenException extends RuntimeException {

    public InvalidTeamInviteTokenException() {
        super("Convite inválido ou expirado.");
    }
}
