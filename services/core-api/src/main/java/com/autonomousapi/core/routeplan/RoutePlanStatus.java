package com.autonomousapi.core.routeplan;

/**
 * Nunca setado diretamente por um endpoint — {@link RoutePlanService#completeStop} deriva a
 * transição do estado das paradas (ver comentário na migration V20).
 */
public enum RoutePlanStatus {
    PLANEJADA,
    EM_ANDAMENTO,
    CONCLUIDA
}
