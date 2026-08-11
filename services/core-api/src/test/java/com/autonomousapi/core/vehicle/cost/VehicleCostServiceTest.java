package com.autonomousapi.core.vehicle.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryRequest;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleCostServiceTest {

    private final VehicleRepository vehicleRepo = mock(VehicleRepository.class);
    private final VehicleCostEntryRepository costRepo = mock(VehicleCostEntryRepository.class);
    private final VehicleCostService service = new VehicleCostService(vehicleRepo, costRepo);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal =
            new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void calculaCustoPorKm() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 1000);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(costRepo.sumAmountByVehicleId(vehicle.getId())).thenReturn(new BigDecimal("500.00"));

        VehicleCostSummaryResponse summary = service.summary(principal, vehicleId);

        assertEquals(new BigDecimal("500.00"), summary.totalCost());
        assertEquals(1000, summary.odometerKm());
        assertEquals(new BigDecimal("0.50"), summary.costPerKm());
    }

    @Test
    void custoPorKmNuloQuandoOdometroZero() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ZER0000", "VW", "Novo", 2024, 0);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(costRepo.sumAmountByVehicleId(vehicle.getId())).thenReturn(BigDecimal.ZERO);

        VehicleCostSummaryResponse summary = service.summary(principal, vehicleId);

        assertNull(summary.costPerKm());
    }

    @Test
    void naoAdicionaCustoEmVeiculoDeOutroTenant() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.empty());
        VehicleCostEntryRequest req = new VehicleCostEntryRequest(
                VehicleCostCategory.COMBUSTIVEL, new BigDecimal("100.00"), "Abastecimento", LocalDate.now());

        assertThrows(NotFoundException.class, () -> service.addEntry(principal, vehicleId, req));
    }
}
