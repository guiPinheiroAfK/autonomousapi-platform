package com.autonomousapi.core.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Decide entre LoggingPushSender e ExpoPushSender num lugar só (ADR 0016) — mesmo padrão
 * do EmailConfig: um isBlank() de verdade, não @ConditionalOnProperty (que trataria
 * "propriedade presente e vazia" como "presente").
 */
@Configuration
public class PushConfig {

    @Bean
    public PushSender pushSender(
            @Value("${app.push.expo-access-token:}") String expoAccessToken,
            RestClient.Builder restClientBuilder) {
        if (expoAccessToken.isBlank()) {
            return new LoggingPushSender();
        }
        return new ExpoPushSender(restClientBuilder, expoAccessToken);
    }
}
