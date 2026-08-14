package com.autonomousapi.core.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Decide entre LoggingEmailSender e SmtpEmailSender num lugar só, com um `isBlank()` de
 * verdade — não via {@code @ConditionalOnProperty}, que trata "propriedade presente e
 * vazia" (o que `${MAIL_SMTP_HOST:}` sempre resolve para, quando a env var não existe)
 * como "presente" e ativaria o SmtpEmailSender mesmo sem host nenhum configurado.
 */
@Configuration
public class EmailConfig {

    @Bean
    public EmailSender emailSender(
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.mail.from:no-reply@autonomousapi.com.br}") String fromAddress,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        if (smtpHost.isBlank()) {
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(mailSenderProvider.getObject(), fromAddress);
    }
}
