package com.autonomousapi.core.push;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Envia via Expo Push API (o app mobile é Expo/React Native — ADR 0016). Requisição HTTP
 * simples, sem SDK de terceiro: POST em https://exp.host/--/api/v2/push/send com o token
 * de acesso opcional no header (recomendado pela Expo para rate limit mais alto, não
 * obrigatório para o envio funcionar).
 */
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient;
    private final String accessToken;

    public ExpoPushSender(RestClient.Builder restClientBuilder, String accessToken) {
        this.restClient = restClientBuilder.baseUrl(EXPO_PUSH_URL).build();
        this.accessToken = accessToken;
    }

    @Override
    public void sendToToken(String deviceToken, String title, String body) {
        try {
            restClient.post()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("to", deviceToken, "title", title, "body", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // Falha de envio não pode derrubar a operação que disparou o push — mesmo
            // raciocínio do SmtpEmailSender. Log alto: sinal de provedor/token mal configurado.
            log.error("Falha ao enviar push para token {}", deviceToken, ex);
        }
    }
}
