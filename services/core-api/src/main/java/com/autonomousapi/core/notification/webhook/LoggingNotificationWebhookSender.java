package com.autonomousapi.core.notification.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sem `OPERATIONAL_WEBHOOK_URL` configurada — mesmo padrão de `LoggingEmailSender`. */
public class LoggingNotificationWebhookSender implements NotificationWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationWebhookSender.class);

    @Override
    public void notify(String message) {
        log.info("[notificacao operacional] {}", message);
    }
}
