package com.autonomousapi.core.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatCleanupJobTest {

    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatSyncCursorRepository syncCursors = mock(ChatSyncCursorRepository.class);

    private final ChatCleanupJob job = new ChatCleanupJob(conversations, messages, syncCursors);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID gestorUserId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    private ChatMessage messageAt(UUID conversationId, Instant sentAt) {
        ChatMessage m = new ChatMessage(conversationId, gestorUserId, "corpo");
        setField(m, "sentAt", sentAt);
        return m;
    }

    private static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void naoLimpaSemDeviceRegistrado() {
        when(conversations.findDistinctGestorUserIds()).thenReturn(List.of(gestorUserId));
        when(syncCursors.findAllByGestorUserId(gestorUserId)).thenReturn(List.of());

        job.run();

        // Sem device, o job nem chega a olhar conversas — conservador de propósito.
        org.mockito.Mockito.verify(conversations, org.mockito.Mockito.never())
                .findAllByGestorUserIdOrderByCreatedAtDesc(gestorUserId);
    }

    @Test
    void mantemMensagensDentroDaJanelaMesmoComSyncCompleto() {
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, driverId, null);
        ChatMessage recente = messageAt(conv.getId(), Instant.now().minus(1, ChronoUnit.HOURS));

        when(conversations.findDistinctGestorUserIds()).thenReturn(List.of(gestorUserId));
        when(syncCursors.findAllByGestorUserId(gestorUserId))
                .thenReturn(List.of(new ChatSyncCursor(gestorUserId, "device-1", Instant.now())));
        when(conversations.findAllByGestorUserIdOrderByCreatedAtDesc(gestorUserId)).thenReturn(List.of(conv));
        when(messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(conv.getId()))
                .thenReturn(new ArrayList<>(List.of(recente)));

        job.run();

        assertTrue(recente.isAindaNoServidor());
    }

    @Test
    void removeMensagemForaDaJanelaSoDepoisDeConfirmarSyncTotal() {
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, driverId, null);
        ChatMessage antiga = messageAt(conv.getId(), Instant.now().minus(10, ChronoUnit.DAYS));

        when(conversations.findDistinctGestorUserIds()).thenReturn(List.of(gestorUserId));
        // Um device ainda não sincronizou até a mensagem antiga.
        when(syncCursors.findAllByGestorUserId(gestorUserId)).thenReturn(List.of(
                new ChatSyncCursor(gestorUserId, "device-1", Instant.now()),
                new ChatSyncCursor(gestorUserId, "device-2", Instant.now().minus(20, ChronoUnit.DAYS))));
        when(conversations.findAllByGestorUserIdOrderByCreatedAtDesc(gestorUserId)).thenReturn(List.of(conv));
        when(messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(conv.getId()))
                .thenReturn(new ArrayList<>(List.of(antiga)));

        job.run();

        // device-2 (min) ainda não chegou na data da mensagem antiga -> não remove.
        assertTrue(antiga.isAindaNoServidor());
    }

    @Test
    void removeMensagemForaDaJanelaQuandoTodosOsDevicesJaSincronizaram() {
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, driverId, null);
        ChatMessage antiga = messageAt(conv.getId(), Instant.now().minus(10, ChronoUnit.DAYS));

        when(conversations.findDistinctGestorUserIds()).thenReturn(List.of(gestorUserId));
        when(syncCursors.findAllByGestorUserId(gestorUserId))
                .thenReturn(List.of(new ChatSyncCursor(gestorUserId, "device-1", Instant.now())));
        when(conversations.findAllByGestorUserIdOrderByCreatedAtDesc(gestorUserId)).thenReturn(List.of(conv));
        when(messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(conv.getId()))
                .thenReturn(new ArrayList<>(List.of(antiga)));

        job.run();

        assertFalse(antiga.isAindaNoServidor());
    }
}
