package com.autonomousapi.core.notification.dto;

import com.autonomousapi.core.notification.Notification;
import com.autonomousapi.core.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, NotificationType tipo, String titulo, String corpo, String link, boolean lida, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTipo(), n.getTitulo(), n.getCorpo(), n.getLink(), n.isLida(), n.getCreatedAt());
    }
}
