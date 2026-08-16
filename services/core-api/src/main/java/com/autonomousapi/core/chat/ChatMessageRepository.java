package com.autonomousapi.core.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Janela ainda no servidor, em ordem de leitura (mais antiga primeiro). */
    List<ChatMessage> findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtAsc(UUID conversationId);

    /** Mais recente primeiro — usado pelo job de limpeza para achar as top-50. */
    List<ChatMessage> findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(UUID conversationId);
}
