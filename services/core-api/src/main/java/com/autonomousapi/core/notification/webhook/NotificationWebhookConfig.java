package com.autonomousapi.core.notification.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Mesmo raciocínio de {@code EmailConfig}/{@code PassengerNotificationConfig}: `isBlank()`
 * de verdade, não {@code @ConditionalOnProperty} (que não distingue "ausente" de
 * "presente e vazia" quando a env var não existe e o placeholder resolve pra string vazia).
 */
@Configuration
public class NotificationWebhookConfig {

    @Bean
    public NotificationWebhookSender notificationWebhookSender(
            @Value("${app.notification-webhook.url:}") String webhookUrl, RestClient.Builder restClientBuilder) {
        if (webhookUrl.isBlank()) {
            return new LoggingNotificationWebhookSender();
        }
        return new DiscordNotificationWebhookSender(restClientBuilder, webhookUrl);
    }
}
