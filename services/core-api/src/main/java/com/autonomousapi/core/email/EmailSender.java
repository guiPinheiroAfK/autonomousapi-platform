package com.autonomousapi.core.email;

/**
 * Abstração de envio de e-mail (ADR 0011). Duas implementações, escolhidas por config —
 * mesmo padrão do BillingService com a Stripe: funciona sem credencial nenhuma (loga o
 * link em vez de falhar), e vira real assim que houver um provedor SMTP configurado.
 */
public interface EmailSender {

    void sendVerificationEmail(String to, String verificationUrl);

    void sendPasswordResetEmail(String to, String resetUrl);
}
