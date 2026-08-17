package com.autonomousapi.core.chat;

/** ATRIBUICAO_ROTA é sempre gerado pelo backend ({@link ChatService#sendRoutePlanMessage}),
 *  nunca digitado pelo usuário — {@code body} continua preenchido com texto de fallback.
 *  SISTEMA está modelado (spec 07) pra mensagem gerada automaticamente fora de atribuição
 *  de rota — ainda sem nenhum caso de uso gerando esse tipo. */
public enum ChatMessageType {
    TEXTO,
    ATRIBUICAO_ROTA,
    SISTEMA
}
