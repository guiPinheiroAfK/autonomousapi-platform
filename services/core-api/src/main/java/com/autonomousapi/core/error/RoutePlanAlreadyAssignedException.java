package com.autonomousapi.core.error;

/** Rota já designada a outro motorista — anexar no chat ou reatribuir nunca sobrescreve
 *  silenciosamente (achado da revisão do plano). */
public class RoutePlanAlreadyAssignedException extends RuntimeException {

    public RoutePlanAlreadyAssignedException() {
        super("Esta rota já está designada a outro motorista.");
    }
}
