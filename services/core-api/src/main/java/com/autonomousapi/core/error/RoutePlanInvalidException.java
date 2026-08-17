package com.autonomousapi.core.error;

/** Validação de negócio de route_plan/route_stop (data no passado, janela inválida,
 *  contagem de paradas errada pra TRANSFER) — mensagem específica por caso. */
public class RoutePlanInvalidException extends RuntimeException {

    public RoutePlanInvalidException(String message) {
        super(message);
    }
}
