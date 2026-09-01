package com.autonomousapi.core.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReaction, UUID> {

    Optional<ChatMessageReaction> findByMessageIdAndUserId(UUID messageId, UUID userId);

    void deleteByMessageIdAndUserId(UUID messageId, UUID userId);

    /** Batch pra montar a lista de reações de várias mensagens de uma vez (mesmo padrão
     *  anti-N+1 já usado em {@code ChatService#toResponses}). */
    List<ChatMessageReaction> findAllByMessageIdIn(List<UUID> messageIds);
}
