package com.autonomousapi.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);

    private final NotificationService service = new NotificationService(notifications, pushNotificationService);

    private final UUID userId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(userId, UUID.randomUUID(), "GESTOR_FROTA");

    @Test
    void notifyPersistaLinhaEDisparaPush() {
        service.notify(userId, NotificationType.ORCAMENTO_ALERTA, "Título", "Corpo", "/custos");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(NotificationType.ORCAMENTO_ALERTA, saved.getTipo());
        assertEquals("/custos", saved.getLink());
        assertEquals(false, saved.isLida());

        verify(pushNotificationService).notifyUser(userId, "Título", "Corpo");
    }

    @Test
    void markReadRejeitaNotificacaoDeOutroUsuario() {
        UUID id = UUID.randomUUID();
        when(notifications.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.markRead(principal, id));
    }

    @Test
    void markReadMarcaAPropriaNotificacaoComoLida() {
        Notification n = new Notification(userId, NotificationType.AVISO_GESTOR, "Título", "Corpo", null);
        when(notifications.findByIdAndUserId(n.getId(), userId)).thenReturn(Optional.of(n));

        service.markRead(principal, n.getId());

        assertEquals(true, n.isLida());
    }

    @Test
    void markAllReadDelegaParaUpdateEmMassaDoProprioUsuario() {
        service.markAllRead(principal);

        verify(notifications).marcarTodasLidas(userId);
    }
}
