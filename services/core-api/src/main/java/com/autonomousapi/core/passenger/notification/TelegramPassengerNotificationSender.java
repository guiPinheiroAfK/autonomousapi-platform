package com.autonomousapi.core.passenger.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Envia via API do Bot do Telegram — requisição HTTP simples, sem SDK de terceiro (mesmo
 * espírito de {@code ExpoPushSender}): {@code POST https://api.telegram.org/bot<token>/sendMessage}.
 */
public class TelegramPassengerNotificationSender implements PassengerNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramPassengerNotificationSender.class);

    private final RestClient restClient;

    public TelegramPassengerNotificationSender(RestClient.Builder restClientBuilder, String botToken) {
        this.restClient = restClientBuilder.baseUrl("https://api.telegram.org/bot" + botToken).build();
    }

    @Override
    public void sendMessage(long chatId, String text) {
        try {
            restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // Falha de envio não pode derrubar a operação que disparou a notificação — mesmo
            // raciocínio do ExpoPushSender/SmtpEmailSender. Log alto: sinal de bot/token mal
            // configurado, ou passageiro que nunca deu /start (chat_id inválido/revogado).
            log.error("Falha ao enviar mensagem ao passageiro (chatId={})", chatId, ex);
        }
    }
}
