package com.autonomousapi.core.push;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.DriverVehicleAssignment;
import com.autonomousapi.core.driver.DriverVehicleAssignmentRepository;
import com.autonomousapi.core.driver.DriverService;
import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
import com.autonomousapi.core.vehicle.VehicleService;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário de push (spec 07 item 5, ADR 0016): CNH vencendo e manutenção agendada.
 * Reaproveita os mesmos limiares e a mesma lógica de alerta que o painel do gestor já
 * calcula (DriverService/VehicleService) — só troca "quem lê a lista" por "quem recebe push".
 *
 * Limitação conhecida, documentada e aceita (não é bug silencioso): o job roda uma vez por
 * dia e não guarda "já notificado hoje" — se rodar mais de uma vez no mesmo dia (deploy,
 * restart), o motorista pode receber o mesmo aviso de novo. Aceitável no volume esperado;
 * se virar incômodo real, a evolução é uma tabela de "último push enviado por tipo/dia".
 */
@Component
public class AlertPushJob {

    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final DriverRepository drivers;
    private final DriverVehicleAssignmentRepository assignments;
    private final PushNotificationService pushNotificationService;

    public AlertPushJob(
            DriverService driverService,
            VehicleService vehicleService,
            DriverRepository drivers,
            DriverVehicleAssignmentRepository assignments,
            PushNotificationService pushNotificationService) {
        this.driverService = driverService;
        this.vehicleService = vehicleService;
        this.drivers = drivers;
        this.assignments = assignments;
        this.pushNotificationService = pushNotificationService;
    }

    /** Todo dia às 08:00 (horário do servidor). */
    @Scheduled(cron = "0 0 8 * * *")
    public void run() {
        notifyLicenseExpiring();
        notifyMaintenanceDue();
    }

    private void notifyLicenseExpiring() {
        for (DriverLicenseAlertResponse alert : driverService.licenseExpiringAcrossAllTenants()) {
            drivers.findById(alert.driverId()).ifPresent(driver -> {
                String prazo = alert.diasRestantes() < 0 ? "vencida" : "vence em " + alert.diasRestantes() + " dia(s)";
                pushNotificationService.notifyUser(
                        driver.getAppUserId(), "CNH " + prazo, "Sua CNH " + prazo + ". Regularize a documentação.");
            });
        }
    }

    private void notifyMaintenanceDue() {
        for (VehicleMaintenanceAlertResponse alert : vehicleService.maintenanceDueAcrossAllTenants()) {
            Optional<DriverVehicleAssignment> assignment =
                    assignments.findByVehicleIdAndEndedAtIsNull(alert.vehicleId());
            assignment.ifPresent(a -> drivers.findById(a.getDriverId())
                    .filter(Driver::hasLogin)
                    .ifPresent(driver -> pushNotificationService.notifyUser(
                            driver.getAppUserId(),
                            "Manutenção agendada",
                            "O veículo " + alert.plate() + " tem manutenção agendada em breve.")));
        }
    }
}
