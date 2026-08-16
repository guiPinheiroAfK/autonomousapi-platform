package com.autonomousapi.core.push;

/**
 * Abstração de envio de push (ADR 0016). Mesmo padrão do EmailSender e do BillingService
 * com a Stripe: funciona sem credencial nenhuma (loga em vez de falhar), e vira real assim
 * que houver um provedor configurado.
 */
public interface PushSender {

    void sendToToken(String deviceToken, String title, String body);
}
