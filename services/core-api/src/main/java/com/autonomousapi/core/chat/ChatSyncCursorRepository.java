package com.autonomousapi.core.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSyncCursorRepository extends JpaRepository<ChatSyncCursor, UUID> {

    Optional<ChatSyncCursor> findByGestorUserIdAndDeviceId(UUID gestorUserId, String deviceId);

    List<ChatSyncCursor> findAllByGestorUserId(UUID gestorUserId);
}
