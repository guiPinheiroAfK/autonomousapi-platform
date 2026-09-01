package com.autonomousapi.core.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByGestorUserIdAndDriverId(UUID gestorUserId, UUID driverId);

    List<ChatConversation> findAllByGestorUserIdOrderByCreatedAtDesc(UUID gestorUserId);

    List<ChatConversation> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId);

    Optional<ChatConversation> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Cross-tenant de propósito: usado só pelo job de limpeza (ADR 0015), que roda uma vez
     *  para toda a base, agrupando por gestor. */
    @Query("select distinct c.gestorUserId from ChatConversation c")
    List<UUID> findDistinctGestorUserIds();

    /** V33, chat em equipe — busca o par já ordenado (ver {@code ChatConversation.novaConversaEquipe}). */
    Optional<ChatConversation> findByTenantIdAndKindAndGestorUserIdAndParticipantBUserId(
            UUID tenantId, ChatConversationKind kind, UUID gestorUserId, UUID participantBUserId);

    /** V33 — completa {@link #findAllByGestorUserIdOrderByCreatedAtDesc} pro caso em que o
     *  usuário é o segundo participante (não o "menor UUID") de uma conversa EQUIPE. */
    List<ChatConversation> findAllByKindAndParticipantBUserId(ChatConversationKind kind, UUID participantBUserId);
}
