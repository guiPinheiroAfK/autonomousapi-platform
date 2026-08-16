package com.autonomousapi.core.chat;

/** ROUTE_ASSIGNMENT é sempre gerado pelo backend ({@link ChatService#sendRoutePlanMessage}),
 *  nunca digitado pelo usuário — {@code body} continua preenchido com texto de fallback. */
public enum ChatMessageType {
    TEXT,
    ROUTE_ASSIGNMENT
}
