package com.autonomousapi.core.passenger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sem bot do Telegram configurado (padrão dev/demo), a mensagem não sai de verdade —
 *  fica no log. Mesmo padrão de {@code LoggingEmailSender}/{@code LoggingPushSender}. */
public class LoggingPassengerNotificationSender implements PassengerNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPassengerNotificationSender.class);

    @Override
    public void sendMessage(long chatId, String text) {
        log.info("[mensagem ao passageiro — sem bot do Telegram configurado] chatId={} texto={}", chatId, text);
    }
}
