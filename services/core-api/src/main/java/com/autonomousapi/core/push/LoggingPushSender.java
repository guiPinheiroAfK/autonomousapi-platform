package com.autonomousapi.core.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sem provedor configurado (padrão dev/demo), o push não sai de verdade — fica no log.
 * Permite testar o mecanismo inteiro (evento → push disparado) sem credencial nenhuma.
 */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void sendToToken(String deviceToken, String title, String body) {
        log.info("[push — sem provedor configurado] token={} titulo={} corpo={}", deviceToken, title, body);
    }
}
