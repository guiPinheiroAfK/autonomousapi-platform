package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.error.VehicleAlreadyAssignedException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Designação motorista→veículo (ADR 0014). Escrita é só do gestor. A invariante "no
 * máximo uma designação ativa por motorista e por veículo" é garantida por índice parcial
 * no banco; aqui o service a respeita de forma explícita (encerra a atual antes de criar).
 */
@Service
public class DriverAssignmentService {

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverVehicleAssignmentRepository assignments;

    public DriverAssignmentService(
            DriverRepository drivers,
            VehicleRepository vehicles,
            DriverVehicleAssignmentRepository assignments) {
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.assignments = assignments;
    }

    @Transactional
    public DriverAssignmentResponse assign(JwtPrincipal principal, UUID driverId, UUID vehicleId) {
        UUID tenantId = principal.tenantId();
        Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, tenantId), "Motorista não encontrado.");
        Vehicle vehicle = Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, tenantId), "Veículo não encontrado.");

        Optional<DriverVehicleAssignment> vehicleActive =
                assignments.findByVehicleIdAndEndedAtIsNull(vehicleId);
        if (vehicleActive.isPresent()) {
            // Já designado a este mesmo motorista: idempotente, devolve a designação vigente.
            if (vehicleActive.get().getDriverId().equals(driverId)) {
                return DriverAssignmentResponse.from(vehicleActive.get(), vehicle);
            }
            // Designado a outro motorista: conflito explícito, gestor encerra antes.
            throw new VehicleAlreadyAssignedException();
        }

        // Trocar de veículo: encerra a designação atual do motorista antes de abrir a nova.
        assignments.findByDriverIdAndEndedAtIsNull(driverId)
                .ifPresent(DriverVehicleAssignment::end);

        DriverVehicleAssignment created =
                assignments.save(new DriverVehicleAssignment(tenantId, driverId, vehicleId));
        return DriverAssignmentResponse.from(created, vehicle);
    }

    @Transactional
    public void end(JwtPrincipal principal, UUID driverId) {
        Driver driver = Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, principal.tenantId()), "Motorista não encontrado.");
        DriverVehicleAssignment active = Lookups.orNotFound(
                assignments.findByDriverIdAndEndedAtIsNull(driver.getId()), "Motorista não tem designação ativa.");
        active.end();
    }

    /** Designação ativa do motorista para exibição ao gestor (null se não houver). */
    @Transactional(readOnly = true)
    public DriverAssignmentResponse activeForDriver(JwtPrincipal principal, UUID driverId) {
        Driver driver = Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, principal.tenantId()), "Motorista não encontrado.");
        return assignments.findByDriverIdAndEndedAtIsNull(driver.getId())
                .map(a -> DriverAssignmentResponse.from(
                        a,
                        Lookups.orNotFound(
                                vehicles.findByIdAndTenantId(a.getVehicleId(), principal.tenantId()), "Veículo não encontrado.")))
                .orElse(null);
    }
}
