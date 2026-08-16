package com.autonomousapi.core.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.DriverService;
import com.autonomousapi.core.driver.DriverVehicleAssignment;
import com.autonomousapi.core.driver.DriverVehicleAssignmentRepository;
import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
import com.autonomousapi.core.vehicle.VehicleService;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertPushJobTest {

    private final DriverService driverService = mock(DriverService.class);
    private final VehicleService vehicleService = mock(VehicleService.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final DriverVehicleAssignmentRepository assignments = mock(DriverVehicleAssignmentRepository.class);
    private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);

    private final AlertPushJob job =
            new AlertPushJob(driverService, vehicleService, drivers, assignments, pushNotificationService);

    @Test
    void notificaCnhVencendoParaMotoristaComLogin() {
        UUID driverId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        Driver d = new Driver(UUID.randomUUID(), "João", "12345678901", null);
        d.linkAppUser(appUserId);
        when(driverService.licenseExpiringAcrossAllTenants())
                .thenReturn(List.of(new DriverLicenseAlertResponse(driverId, "João", LocalDate.now().plusDays(5), 5)));
        when(drivers.findById(driverId)).thenReturn(Optional.of(d));
        when(vehicleService.maintenanceDueAcrossAllTenants()).thenReturn(List.of());

        job.run();

        verify(pushNotificationService).notifyUser(eq(appUserId), any(), any());
    }

    @Test
    void notificaManutencaoParaMotoristaDesignadoComLogin() {
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        Driver d = new Driver(UUID.randomUUID(), "João", "12345678901", null);
        d.linkAppUser(appUserId);
        DriverVehicleAssignment assignment =
                new DriverVehicleAssignment(UUID.randomUUID(), driverId, vehicleId);
        when(driverService.licenseExpiringAcrossAllTenants()).thenReturn(List.of());
        when(vehicleService.maintenanceDueAcrossAllTenants()).thenReturn(List.of(
                new VehicleMaintenanceAlertResponse(vehicleId, "ABC1D23", "Fiat", "Toro", null, null, null, 500)));
        when(assignments.findByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.of(assignment));
        when(drivers.findById(driverId)).thenReturn(Optional.of(d));

        job.run();

        verify(pushNotificationService).notifyUser(eq(appUserId), any(), any());
    }

    @Test
    void naoNotificaManutencaoSemDesignacaoAtiva() {
        UUID vehicleId = UUID.randomUUID();
        when(driverService.licenseExpiringAcrossAllTenants()).thenReturn(List.of());
        when(vehicleService.maintenanceDueAcrossAllTenants()).thenReturn(List.of(
                new VehicleMaintenanceAlertResponse(vehicleId, "ABC1D23", "Fiat", "Toro", null, null, null, 500)));
        when(assignments.findByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.empty());

        job.run();

        verify(pushNotificationService, never()).notifyUser(any(), any(), any());
    }

    @Test
    void naoNotificaManutencaoQuandoMotoristaDesignadoNaoTemLogin() {
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Driver semLogin = new Driver(UUID.randomUUID(), "Maria", "98765432100", null);
        DriverVehicleAssignment assignment =
                new DriverVehicleAssignment(UUID.randomUUID(), driverId, vehicleId);
        when(driverService.licenseExpiringAcrossAllTenants()).thenReturn(List.of());
        when(vehicleService.maintenanceDueAcrossAllTenants()).thenReturn(List.of(
                new VehicleMaintenanceAlertResponse(vehicleId, "ABC1D23", "Fiat", "Toro", null, null, null, 500)));
        when(assignments.findByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.of(assignment));
        when(drivers.findById(driverId)).thenReturn(Optional.of(semLogin));

        job.run();

        verify(pushNotificationService, never()).notifyUser(any(), any(), any());
    }
}
