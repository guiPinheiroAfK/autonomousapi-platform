package com.autonomousapi.core.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverAssignmentService;
import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.TripService;
import com.autonomousapi.core.trip.dto.TripResponse;
import com.autonomousapi.core.vehicle.condition.IncidentSeverity;
import com.autonomousapi.core.vehicle.condition.VehicleConditionService;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentRequest;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentResponse;
import com.autonomousapi.core.workorder.WorkOrderService;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeServiceTest {

    private final CurrentDriverResolver driverResolver = mock(CurrentDriverResolver.class);
    private final DriverAssignmentService assignmentService = mock(DriverAssignmentService.class);
    private final WorkOrderService workOrderService = mock(WorkOrderService.class);
    private final TripService tripService = mock(TripService.class);
    private final VehicleConditionService conditionService = mock(VehicleConditionService.class);

    private final MeService service = new MeService(
            driverResolver, assignmentService, workOrderService, tripService, conditionService);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

    private Driver driver() {
        return new Driver(tenantId, "João Motorista", "12345678901", null);
    }

    @Test
    void profileResolveApenasOProprioDriverDoToken() {
        Driver d = driver();
        when(driverResolver.resolve(principal)).thenReturn(d);

        var resp = service.profile(principal);

        assertEquals(d.getId(), resp.id());
        assertEquals("João Motorista", resp.name());
        // DriverProfileResponse não tem campo de avaliação — não há o que vazar aqui.
    }

    @Test
    void vehicleDevolveNullQuandoSemDesignacaoAtiva() {
        Driver d = driver();
        when(driverResolver.resolve(principal)).thenReturn(d);
        when(assignmentService.activeForDriver(principal, d.getId())).thenReturn(null);

        assertNull(service.vehicle(principal));
    }

    @Test
    void vehicleWorkOrdersVazioSemVeiculoDesignado() {
        Driver d = driver();
        when(driverResolver.resolve(principal)).thenReturn(d);
        when(assignmentService.activeForDriver(principal, d.getId())).thenReturn(null);

        List<WorkOrderResponse> result = service.vehicleWorkOrders(principal);

        assertTrue(result.isEmpty());
        verify(workOrderService, never()).list(any(), any());
    }

    @Test
    void vehicleWorkOrdersUsaOVeiculoDaDesignacaoAtiva() {
        Driver d = driver();
        UUID vehicleId = UUID.randomUUID();
        DriverAssignmentResponse assignment = new DriverAssignmentResponse(
                UUID.randomUUID(), d.getId(), vehicleId, "ABC1D23", "Fiat", "Toro", Instant.now());
        when(driverResolver.resolve(principal)).thenReturn(d);
        when(assignmentService.activeForDriver(principal, d.getId())).thenReturn(assignment);
        when(workOrderService.list(principal, vehicleId)).thenReturn(List.of());

        service.vehicleWorkOrders(principal);

        verify(workOrderService).list(principal, vehicleId);
    }

    @Test
    void tripsDelegaDiretoParaTripServiceJaEscopadoPorUserId() {
        TripResponse trip = new TripResponse(UUID.randomUUID(), UUID.randomUUID(), "EM_ANDAMENTO", Instant.now(), null);
        when(tripService.list(principal)).thenReturn(List.of(trip));

        List<TripResponse> result = service.trips(principal);

        assertEquals(1, result.size());
        verify(tripService).list(principal);
    }

    @Test
    void reportIncidentRejeitaSemVeiculoDesignado() {
        Driver d = driver();
        when(driverResolver.resolve(principal)).thenReturn(d);
        when(assignmentService.activeForDriver(principal, d.getId())).thenReturn(null);
        VehicleIncidentRequest req =
                new VehicleIncidentRequest(LocalDate.now(), IncidentSeverity.LEVE, null, null);

        assertThrows(NotFoundException.class, () -> service.reportIncident(principal, req));
        verify(conditionService, never()).registerIncident(any(), any(), any());
    }

    @Test
    void reportIncidentUsaVeiculoDaDesignacaoAtivaNuncaDoCliente() {
        Driver d = driver();
        UUID vehicleId = UUID.randomUUID();
        DriverAssignmentResponse assignment = new DriverAssignmentResponse(
                UUID.randomUUID(), d.getId(), vehicleId, "ABC1D23", "Fiat", "Toro", Instant.now());
        VehicleIncidentRequest req =
                new VehicleIncidentRequest(LocalDate.now(), IncidentSeverity.MODERADA, "Colisão leve", null);
        when(driverResolver.resolve(principal)).thenReturn(d);
        when(assignmentService.activeForDriver(principal, d.getId())).thenReturn(assignment);
        VehicleIncidentResponse createdResp = new VehicleIncidentResponse(
                UUID.randomUUID(), vehicleId, req.data(), req.severidade(), req.descricao(), req.custoReparo());
        when(conditionService.registerIncident(eq(principal), eq(vehicleId), eq(req)))
                .thenReturn(createdResp);

        service.reportIncident(principal, req);

        verify(conditionService).registerIncident(principal, vehicleId, req);
    }
}
