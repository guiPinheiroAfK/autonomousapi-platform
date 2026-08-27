package com.autonomousapi.core.notification;

import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.notification.dto.NotificationResponse;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto único onde "usuário precisa ser avisado" vira as duas entregas que já existiam
 * separadas: push no aparelho (ADR 0016) e, agora, uma linha persistida pro sino do
 * topbar web. Quem dispara um evento (BudgetAlertJob, AlertPushJob,
 * DriverNotificationService) chama só este serviço — não precisa saber que existem dois
 * canais.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final PushNotificationService pushNotificationService;

    public NotificationService(NotificationRepository notifications, PushNotificationService pushNotificationService) {
        this.notifications = notifications;
        this.pushNotificationService = pushNotificationService;
    }

    @Transactional
    public void notify(UUID userId, NotificationType tipo, String titulo, String corpo, String link) {
        notifications.save(new Notification(userId, tipo, titulo, corpo, link));
        pushNotificationService.notifyUser(userId, titulo, corpo);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(JwtPrincipal principal, Pageable pageable) {
        return notifications
                .findByUserIdOrderByCreatedAtDesc(principal.userId(), pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(JwtPrincipal principal) {
        return notifications.countByUserIdAndLidaFalse(principal.userId());
    }

    @Transactional
    public void markRead(JwtPrincipal principal, UUID id) {
        Notification n = Lookups.orNotFound(
                notifications.findByIdAndUserId(id, principal.userId()), "Notificação não encontrada.");
        n.marcarLida();
    }

    @Transactional
    public void markAllRead(JwtPrincipal principal) {
        notifications.marcarTodasLidas(principal.userId());
    }
}
