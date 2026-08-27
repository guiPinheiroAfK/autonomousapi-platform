package com.autonomousapi.core.vehicle.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VehicleConditionServiceTest {

    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final VehicleIncidentRepository incidents = mock(VehicleIncidentRepository.class);
    private final VehicleConditionScoreRepository scores = mock(VehicleConditionScoreRepository.class);

    private final VehicleConditionService service = new VehicleConditionService(vehicles, incidents, scores);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void deleteIncidentRemoveERecalculaScoreSemEle() {
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "Fiat", "Fiorino", 2022, 1000);
        VehicleIncident grave = new VehicleIncident(vehicle.getId(), LocalDate.now(), IncidentSeverity.GRAVE, null, null);
        when(vehicles.findByIdAndTenantId(vehicle.getId(), tenantId)).thenReturn(Optional.of(vehicle));
        when(incidents.findByIdAndVehicleId(grave.getId(), vehicle.getId())).thenReturn(Optional.of(grave));
        // Depois de apagar o único sinistro, a lista pra recalcular já vem vazia.
        when(incidents.findAllByVehicleIdOrderByDataDesc(vehicle.getId())).thenReturn(List.of());
        when(scores.findByVehicleId(vehicle.getId())).thenReturn(Optional.empty());

        service.deleteIncident(principal, vehicle.getId(), grave.getId());

        verify(incidents).delete(grave);
        ArgumentCaptor<VehicleConditionScore> captor = ArgumentCaptor.forClass(VehicleConditionScore.class);
        verify(scores).save(captor.capture());
        // Sem nenhum sinistro restante, o score volta pro teto (100 - 0 de penalidade).
        assertEquals(new BigDecimal("100"), captor.getValue().getScore());
    }

    @Test
    void deleteIncidentRejeitaSinistroDeOutroVeiculo() {
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "Fiat", "Fiorino", 2022, 1000);
        UUID incidentIdDeOutroVeiculo = UUID.randomUUID();
        when(vehicles.findByIdAndTenantId(vehicle.getId(), tenantId)).thenReturn(Optional.of(vehicle));
        when(incidents.findByIdAndVehicleId(incidentIdDeOutroVeiculo, vehicle.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.deleteIncident(principal, vehicle.getId(), incidentIdDeOutroVeiculo));
    }
}
