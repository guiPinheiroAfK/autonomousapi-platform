package com.autonomousapi.core.driver;

import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.notification.NotificationService;
import com.autonomousapi.core.notification.NotificationType;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Aviso do gestor" (spec 07 item 5, ADR 0016) — separado do DriverService para não
 *  acoplar CRUD de motorista a notificação. */
@Service
public class DriverNotificationService {

    private final DriverRepository drivers;
    private final NotificationService notificationService;

    public DriverNotificationService(DriverRepository drivers, NotificationService notificationService) {
        this.drivers = drivers;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public void notify(JwtPrincipal principal, UUID driverId, String title, String body) {
        Driver driver = Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, principal.tenantId()), "Motorista não encontrado.");
        if (!driver.hasLogin()) {
            throw new DriverWithoutLoginException();
        }
        notificationService.notify(driver.getAppUserId(), NotificationType.AVISO_GESTOR, title, body, null);
    }
}
