package com.autonomousapi.core.passenger.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Decide entre LoggingPassengerNotificationSender e TelegramPassengerNotificationSender num
 * lugar só (spec 14) — mesmo padrão do EmailConfig/PushConfig: um {@code isBlank()} de
 * verdade, não {@code @ConditionalOnProperty}.
 */
@Configuration
public class PassengerNotificationConfig {

    @Bean
    public PassengerNotificationSender passengerNotificationSender(
            @Value("${app.telegram.bot-token:}") String botToken,
            RestClient.Builder restClientBuilder) {
        if (botToken.isBlank()) {
            return new LoggingPassengerNotificationSender();
        }
        return new TelegramPassengerNotificationSender(restClientBuilder, botToken);
    }
}
