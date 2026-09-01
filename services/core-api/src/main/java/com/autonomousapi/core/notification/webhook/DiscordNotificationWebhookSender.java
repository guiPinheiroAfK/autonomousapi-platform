package com.autonomousapi.core.notification.webhook;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * "Incoming Webhook" nativo do Discord (spec 12) — POST simples de {@code {"content": ...}}
 * na URL do webhook do canal, sem precisar criar/gerenciar um bot de verdade.
 */
public class DiscordNotificationWebhookSender implements NotificationWebhookSender {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotificationWebhookSender.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordNotificationWebhookSender(RestClient.Builder restClientBuilder, String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notify(String message) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // Falha de envio não pode derrubar signup/verifyEmail — mesmo raciocínio de
            // SmtpEmailSender/ExpoPushSender. Log alto: sinal de webhook mal configurado ou
            // canal removido no Discord.
            log.error("Falha ao enviar notificacao operacional ao Discord", ex);
        }
    }
}
