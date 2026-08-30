package com.autonomousapi.core.error;

/** Lançada quando GOOGLE_CLIENT_ID não está configurado (dev/demo). Falha de verificação do
 *  ID token em si (assinatura, audience, e-mail não verificado) vira {@link
 *  InvalidCredentialsException} — não é "esquecemos de configurar", é "token inválido". */
public class GoogleAuthNotConfiguredException extends RuntimeException {
    public GoogleAuthNotConfiguredException(String message) {
        super(message);
    }
}
