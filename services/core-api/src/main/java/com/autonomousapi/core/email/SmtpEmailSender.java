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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Confirme sua conta — AutonomousAPI");
        message.setText(
                "Falta um passo para começar a usar a AutonomousAPI.\n\n"
                        + "Confirme seu e-mail clicando no link abaixo (válido por 24 horas):\n"
                        + verificationUrl + "\n\n"
                        + "Se você não criou essa conta, pode ignorar este e-mail.");
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // Falha de envio não pode derrubar o signup em si — o usuário já foi criado
            // (desabilitado) e pode pedir reenvio depois. Log alto porque é sinal de
            // provedor mal configurado, não de erro do usuário.
            log.error("Falha ao enviar e-mail de verificação para {}", to, ex);
        }
    }
}
