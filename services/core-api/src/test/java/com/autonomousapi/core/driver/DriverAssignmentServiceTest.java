package com.autonomousapi.core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.VehicleAlreadyAssignedException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverAssignmentServiceTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final DriverVehicleAssignmentRepository assignments =
            mock(DriverVehicleAssignmentRepository.class);

    private final DriverAssignmentService service =
            new DriverAssignmentService(drivers, vehicles, assignments);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    private Driver driver() {
        return new Driver(tenantId, "João", "12345678901", null);
    }

    private Vehicle vehicle() {
        return new Vehicle(tenantId, "ABC1D23", "Fiat", "Toro", 2023, 500);
    }

    @Test
    void designaVeiculoLivreAoMotorista() {
        Driver d = driver();
        UUID vehicleId = UUID.randomUUID();
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle()));
        when(assignments.findByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.empty());
        when(assignments.findByDriverIdAndEndedAtIsNull(d.getId())).thenReturn(Optional.empty());
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DriverAssignmentResponse resp = service.assign(principal, d.getId(), vehicleId);

        assertEquals("ABC1D23", resp.plate());
        verify(assignments).save(any(DriverVehicleAssignment.class));
    }

    @Test
    void rejeitaVeiculoJaDesignadoAOutroMotorista() {
        Driver d = driver();
        UUID vehicleId = UUID.randomUUID();
        DriverVehicleAssignment deOutro =
                new DriverVehicleAssignment(tenantId, UUID.randomUUID(), vehicleId);
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle()));
        when(assignments.findByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.of(deOutro));

        assertThrows(VehicleAlreadyAssignedException.class,
                () -> service.assign(principal, d.getId(), vehicleId));
        verify(assignments, never()).save(any());
    }

    @Test
    void trocarDeVeiculoEncerraDesignacaoAtualDoMotorista() {
        Driver d = driver();
        UUID novoVeiculo = UUID.randomUUID();
        DriverVehicleAssignment atual =
                new DriverVehicleAssignment(tenantId, d.getId(), UUID.randomUUID());
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(vehicles.findByIdAndTenantId(novoVeiculo, tenantId)).thenReturn(Optional.of(vehicle()));
        when(assignments.findByVehicleIdAndEndedAtIsNull(novoVeiculo)).thenReturn(Optional.empty());
        when(assignments.findByDriverIdAndEndedAtIsNull(d.getId())).thenReturn(Optional.of(atual));
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assign(principal, d.getId(), novoVeiculo);

        assertFalse(atual.isActive());
        verify(assignments).save(any(DriverVehicleAssignment.class));
    }

    @Test
    void encerrarSemDesignacaoAtivaDaNotFound() {
        Driver d = driver();
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(assignments.findByDriverIdAndEndedAtIsNull(d.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.end(principal, d.getId()));
    }
}
