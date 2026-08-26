package com.autonomousapi.core.chat;

/**
 * Todos os tipos abaixo (exceto TEXTO) são sempre gerados pelo backend, nunca digitados
 * pelo usuário — {@code body} continua preenchido com texto de fallback pra quem não
 * interpreta {@code messageType} (push, cliente desatualizado).
 *
 * <p>ADR 0021 (cancelamento/reatribuição): CANCELAMENTO_ROTA e TROCA_MOTORISTA são ações
 * do gestor (cancelam/reatribuem de verdade, ver {@link ChatService}); SOLICITACAO_* são do
 * motorista — ele nunca decide sozinho, só pede, e o gestor decide respondendo com a ação
 * correspondente (ou uma mensagem TEXTO comum, se recusar).
 *
 * <p>SISTEMA está modelado (spec 07) pra mensagem gerada automaticamente fora desses casos
 * — ainda sem nenhum caso de uso gerando esse tipo.
 */
public enum ChatMessageType {
    TEXTO,
    ATRIBUICAO_ROTA,
    CANCELAMENTO_ROTA,
    TROCA_MOTORISTA,
    SOLICITACAO_CANCELAMENTO,
    SOLICITACAO_TROCA_MOTORISTA,
    SISTEMA
}
