package com.autonomousapi.core.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sem SMTP configurado (padrão dev/demo), o e-mail não sai de verdade — o link fica no
 * log. Isso é o que permite testar o fluxo inteiro (signup → clicar no link → conta
 * habilitada) sem depender de credencial de provedor nenhum. Escolhido por EmailConfig,
 * não por Spring — ver o porquê lá (@ConditionalOnProperty não distingue "ausente" de
 * "presente e vazio", e um placeholder sem valor no application.yml resolve pro segundo).
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        log.info("[email de verificação — sem SMTP configurado] destinatário={} link={}", to, verificationUrl);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetUrl) {
        log.info("[email de redefinição de senha — sem SMTP configurado] destinatário={} link={}", to, resetUrl);
    }

    @Override
    public void sendDriverInviteEmail(String to, String inviteUrl) {
        log.info("[email de convite de motorista — sem SMTP configurado] destinatário={} link={}", to, inviteUrl);
    }

    @Override
    public void sendTeamInviteEmail(String to, String nome, String inviteUrl) {
        log.info("[email de convite de equipe — sem SMTP configurado] destinatário={} nome={} link={}", to, nome, inviteUrl);
    }
}
