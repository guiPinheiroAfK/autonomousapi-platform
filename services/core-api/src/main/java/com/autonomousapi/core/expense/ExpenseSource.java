package com.autonomousapi.core.expense;

/**
 * Origem do lançamento (spec 10). ROUTE_PLAN é reservado para quando um TRANSFER concluído
 * gerar despesa automática — nenhum produtor existe ainda (ver RoutePlanService); não é
 * campo morto/esquecido, é escopo propositalmente deixado de fora deste corte.
 */
public enum ExpenseSource {
    MANUAL,
    ROUTE_PLAN
}
