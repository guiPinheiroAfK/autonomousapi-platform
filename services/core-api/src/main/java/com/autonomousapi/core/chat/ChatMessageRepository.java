package com.autonomousapi.core.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Janela ainda no servidor, em ordem de leitura (mais antiga primeiro). */
    List<ChatMessage> findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtAsc(UUID conversationId);

    /** Mais recente primeiro — usado pelo job de limpeza para achar as top-50. */
    List<ChatMessage> findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(UUID conversationId);

    /** Prévia da lista de conversas (redesenho visual) — só a janela ainda no servidor,
     *  consistente com o que a lista de conversas consegue mostrar sem histórico local. */
    Optional<ChatMessage> findFirstByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(UUID conversationId);

    /** Mensagens do outro participante ainda não marcadas como lidas — usado por markAsRead. */
    List<ChatMessage> findAllByConversationIdAndSenderUserIdNotAndLidoEmIsNull(UUID conversationId, UUID senderUserId);
}
