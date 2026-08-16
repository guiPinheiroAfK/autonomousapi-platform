package com.autonomousapi.core.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpeza do chat (spec 07, ADR 0015): remove do servidor as mensagens fora da janela
 * (últimas 50 OU últimos 7 dias, o que vier primeiro — o mais restritivo dos dois), e
 * SÓ quando todos os dispositivos do gestor já confirmaram ter sincronizado aquele trecho.
 * Sem dispositivo registrado para o gestor, nada é removido — conservador de propósito
 * (nunca apagar sem confirmação de que já foi persistido em algum lugar).
 */
@Component
public class ChatCleanupJob {

    private static final int WINDOW_SIZE = 50;
    private static final Duration WINDOW_AGE = Duration.ofDays(7);

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatSyncCursorRepository syncCursors;

    public ChatCleanupJob(
            ChatConversationRepository conversations,
            ChatMessageRepository messages,
            ChatSyncCursorRepository syncCursors) {
        this.conversations = conversations;
        this.messages = messages;
        this.syncCursors = syncCursors;
    }

    /** De hora em hora — o chat é mais sensível a tempo que os alertas diários (ADR 0016). */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void run() {
        for (UUID gestorUserId : conversations.findDistinctGestorUserIds()) {
            Optional<Instant> minSyncedAt = minSyncedAtAcrossDevices(gestorUserId);
            if (minSyncedAt.isEmpty()) {
                continue;
            }
            for (ChatConversation conversation : conversations.findAllByGestorUserIdOrderByCreatedAtDesc(gestorUserId)) {
                cleanupConversation(conversation, minSyncedAt.get());
            }
        }
    }

    /** Vazio se o gestor não tem nenhum device registrado — job não age nesse caso. */
    private Optional<Instant> minSyncedAtAcrossDevices(UUID gestorUserId) {
        List<ChatSyncCursor> cursors = syncCursors.findAllByGestorUserId(gestorUserId);
        if (cursors.isEmpty()) {
            return Optional.empty();
        }
        return cursors.stream().map(ChatSyncCursor::getSyncedAt).min(Comparator.naturalOrder());
    }

    private void cleanupConversation(ChatConversation conversation, Instant minSyncedAt) {
        List<ChatMessage> desc = messages
                .findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(conversation.getId());
        Instant cutoff = Instant.now().minus(WINDOW_AGE);

        for (int i = 0; i < desc.size(); i++) {
            ChatMessage message = desc.get(i);
            boolean dentroDaJanela = i < WINDOW_SIZE && message.getSentAt().isAfter(cutoff);
            if (dentroDaJanela) {
                continue;
            }
            boolean gestorJaSincronizou = !message.getSentAt().isAfter(minSyncedAt);
            if (gestorJaSincronizou) {
                message.removerDoServidor();
            }
        }
    }
}
