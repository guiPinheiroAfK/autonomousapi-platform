package com.autonomousapi.core.error;

/** Editar/excluir recusado — não é o autor, não é {@code TEXTO}, ou a mensagem já saiu da
 *  janela de retenção do servidor ({@code aindaNoServidor = false}, ChatCleanupJob) e o
 *  outro lado não teria como ver a mudança. Mensagem específica por caso. */
public class ChatMessageActionInvalidException extends RuntimeException {

    public ChatMessageActionInvalidException(String message) {
        super(message);
    }
}
