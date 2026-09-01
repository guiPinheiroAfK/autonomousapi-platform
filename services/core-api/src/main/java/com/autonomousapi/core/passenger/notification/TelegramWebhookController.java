package com.autonomousapi.core.passenger.notification;

import com.autonomousapi.core.passenger.PassengerService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe os updates do bot do Telegram (spec 14) — público, sem JWT (a própria Telegram
 * chama isso, mesmo espírito de {@code BillingController.webhook}). Único comando tratado
 * é {@code /start <token>}, disparado quando o passageiro clica no deep-link — o resto do
 * update é ignorado silenciosamente.
 *
 * <p>Sempre devolve 200: Telegram reenvia (com backoff) updates que não recebem 2xx, e um
 * update malformado ou de um chat qualquer não é motivo pra Telegram insistir.
 */
@RestController
@RequestMapping("/v1/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String START_PREFIX = "/start ";

    private final PassengerService passengerService;
    private final String secretToken;

    public TelegramWebhookController(
            PassengerService passengerService, @Value("${app.telegram.webhook-secret:}") String secretToken) {
        this.passengerService = passengerService;
        this.secretToken = secretToken;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String receivedSecret,
            @RequestBody JsonNode update) {
        // Sem secret configurado (dev/demo), não valida — mesmo raciocínio de "funciona sem
        // credencial nenhuma" do resto do produto. Com secret configurado, qualquer chamada
        // sem o header certo é ignorada (não é a Telegram de verdade, ou é forjada tentando
        // vincular o chat_id de outra pessoa a um passageiro alheio via token adivinhado).
        if (!secretToken.isBlank() && !secretToken.equals(receivedSecret)) {
            log.warn("Webhook do Telegram recebido com secret inválido — ignorado.");
            return;
        }

        JsonNode message = update.path("message");
        String text = message.path("text").asText("");
        if (!text.startsWith(START_PREFIX)) {
            return;
        }
        long chatId = message.path("chat").path("id").asLong();
        if (chatId == 0) {
            return;
        }
        String token = text.substring(START_PREFIX.length()).trim();
        passengerService.vincularTelegram(token, chatId);
    }
}
