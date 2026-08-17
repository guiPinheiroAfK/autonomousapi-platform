package com.autonomousapi.core.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Indicador de "digitando" (spec 07) — estado puramente efêmero, em memória, nunca
 * persistido. Um sinal que expira em segundos não precisa sobreviver a um restart do
 * servidor; guardar isso em banco seria escrita/leitura constante pra um dado que ninguém
 * precisa consultar depois. Mesmo raciocínio de "poll simples, sem infra de tempo real"
 * já aplicado ao resto do chat (ADR 0015).
 *
 * <p>Só funciona corretamente com uma instância do core-api — múltiplas instâncias sem
 * estado compartilhado veriam "digitando" de forma inconsistente. Aceitável pro estágio
 * atual (sem load balancer multi-instância ainda); se isso mudar, mover pra Redis (que já
 * é dependência do projeto, ADR 0007) é a evolução natural, não WebSocket/SSE.
 */
@Component
public class TypingIndicatorService {

    private static final Duration TTL = Duration.ofSeconds(6);

    private final Map<String, Instant> typingUntil = new ConcurrentHashMap<>();

    public void markTyping(UUID conversationId, UUID userId) {
        typingUntil.put(key(conversationId, userId), Instant.now().plus(TTL));
    }

    public boolean isTyping(UUID conversationId, UUID userId) {
        Instant until = typingUntil.get(key(conversationId, userId));
        return until != null && until.isAfter(Instant.now());
    }

    private static String key(UUID conversationId, UUID userId) {
        return conversationId + ":" + userId;
    }
}
