package com.autonomousapi.core.driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.notification.NotificationService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverNotificationServiceTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DriverNotificationService service =
            new DriverNotificationService(drivers, notificationService);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void rejeitaMotoristaSemLogin() {
        Driver d = new Driver(tenantId, "João", "12345678901", null);
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));

        assertThrows(DriverWithoutLoginException.class, () -> service.notify(principal, d.getId(), "Título", "Corpo"));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void rejeitaMotoristaDeOutroTenant() {
        UUID id = UUID.randomUUID();
        when(drivers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.notify(principal, id, "Título", "Corpo"));
    }

    @Test
    void enviaPushParaOAppUserVinculado() {
        Driver d = new Driver(tenantId, "João", "12345678901", null);
        UUID appUserId = UUID.randomUUID();
        d.linkAppUser(appUserId);
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));

        service.notify(principal, d.getId(), "Aviso", "Chegue mais cedo amanhã");

        verify(notificationService).notify(eq(appUserId), any(), eq("Aviso"), eq("Chegue mais cedo amanhã"), any());
    }
}
