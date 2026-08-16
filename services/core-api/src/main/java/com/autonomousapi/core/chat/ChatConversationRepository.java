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
}
