package com.autonomousapi.core.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolhido por EmailConfig quando {@code spring.mail.host} está preenchido de verdade
 * (não só presente-e-vazio). Funciona com qualquer provedor SMTP genérico (Resend, SES,
 * Mailgun, Gmail com senha de app) via config — sem amarrar o código a um SDK de
 * provedor específico.
 *
 * Texto puro de propósito nesta primeira versão: HTML bonito é próxima iteração, não
 * bloqueador do fluxo funcionar de verdade.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        send(to, "Confirme sua conta — AutonomousAPI",
                "Falta um passo para começar a usar a AutonomousAPI.\n\n"
                        + "Confirme seu e-mail clicando no link abaixo (válido por 24 horas):\n"
                        + verificationUrl + "\n\n"
                        + "Se você não criou essa conta, pode ignorar este e-mail.");
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetUrl) {
        send(to, "Redefinir senha — AutonomousAPI",
                "Pediram uma redefinição de senha para esta conta.\n\n"
                        + "Clique no link abaixo para escolher uma nova senha (válido por 1 hora):\n"
                        + resetUrl + "\n\n"
                        + "Se não foi você, pode ignorar este e-mail — sua senha continua a mesma.");
    }

    @Override
    public void sendDriverInviteEmail(String to, String inviteUrl) {
        send(to, "Você foi convidado para a AutonomousAPI",
                "O gestor da sua frota criou um acesso para você no app da AutonomousAPI.\n\n"
                        + "Defina sua senha clicando no link abaixo (válido por 3 dias):\n"
                        + inviteUrl + "\n\n"
                        + "Depois de definir a senha, é só entrar no app com seu e-mail.");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // Falha de envio não pode derrubar a operação que disparou o e-mail — o
            // usuário pode pedir reenvio depois. Log alto porque é sinal de provedor
            // mal configurado, não de erro do usuário.
            log.error("Falha ao enviar e-mail para {}", to, ex);
        }
    }
}
