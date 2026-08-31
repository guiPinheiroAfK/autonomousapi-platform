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

    /**
     * Batch da prévia acima para telas de listagem (evita N+1 em
     * {@code ChatService#listConversations}) — a janela ainda no servidor é curta por
     * conversa (retenção de 7 dias / 50 msgs, ADR 0015), então trazer tudo e escolher a mais
     * recente por conversa em memória é barato mesmo sem uma query "top-1 por grupo".
     */
    List<ChatMessage> findAllByConversationIdInAndAindaNoServidorTrueOrderBySentAtDesc(List<UUID> conversationIds);

    /** Mensagens do outro participante ainda não marcadas como lidas — usado por markAsRead. */
    List<ChatMessage> findAllByConversationIdAndSenderUserIdNotAndLidoEmIsNull(UUID conversationId, UUID senderUserId);

    /** Escopo por conversa — usado por editar/excluir/reagir, mesmo padrão de
     *  {@code findByIdAndTenantId} usado alhures pra não vazar mensagem de outra conversa. */
    Optional<ChatMessage> findByIdAndConversationId(UUID id, UUID conversationId);
}
