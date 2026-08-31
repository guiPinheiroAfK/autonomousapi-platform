package com.autonomousapi.core.error;

/** Papel de equipe inválido pra esse fluxo (spec 15) — convite/troca de papel só aceita
 *  DESPACHANTE/VISUALIZADOR (nunca GESTOR_FROTA/ADMIN, evita escalonamento de privilégio),
 *  e o Gestor não pode mudar o próprio papel por esse endpoint. */
public class InvalidTeamRoleException extends RuntimeException {

    public InvalidTeamRoleException(String message) {
        super(message);
    }
}
