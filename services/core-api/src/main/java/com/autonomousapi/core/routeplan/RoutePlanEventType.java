package com.autonomousapi.core.routeplan;

/** Taxonomia definida no levantamento do trâmite completo (ADR 0020), não só do caminho
 *  feliz — ver docs/levantamento-tramite-rota-2026-08-25.md. */
public enum RoutePlanEventType {
    CRIADA,
    ATRIBUIDA,
    PARADA_CONCLUIDA,
    CONCLUIDA,
    CANCELADA,
    REATRIBUIDA,
    SOLICITACAO_CANCELAMENTO,
    SOLICITACAO_TROCA_MOTORISTA,
    SOLICITACAO_APROVADA
}
