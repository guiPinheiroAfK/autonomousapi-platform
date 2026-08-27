package com.autonomousapi.core.notification;

/** Origem de cada notificação — mesmos disparos que já existiam pra push (ADR 0016). */
public enum NotificationType {
    ORCAMENTO_ALERTA,
    CNH_VENCENDO,
    MANUTENCAO_AGENDADA,
    AVISO_GESTOR,
}
