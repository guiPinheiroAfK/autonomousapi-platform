package com.autonomousapi.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.PlateAlreadyUsedException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import com.autonomousapi.core.vehicle.dto.VehicleRequest;
import com.autonomousapi.core.vehicle.dto.VehicleResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleServiceTest {

    private final VehicleRepository repo = mock(VehicleRepository.class);
    private final VehicleService service = new VehicleService(repo);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final JwtPrincipal principal =
            new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void criaVeiculoNoTenantDoPrincipal() {
        VehicleRequest req = new VehicleRequest("ABC1234", "VW", "Saveiro", 2022, 1000, VehicleStatus.ATIVO, null, null, java.util.Map.of());
        when(repo.existsByTenantIdAndPlateIgnoreCase(tenantId, "ABC1234")).thenReturn(false);

        VehicleResponse resp = service.create(principal, req);

        assertEquals("ABC1234", resp.plate());
        verify(repo).save(any(Vehicle.class));
    }

    @Test
    void rejeitaPlacaDuplicadaNoMesmoTenant() {
        VehicleRequest req = new VehicleRequest("ABC1234", "VW", "Saveiro", 2022, 1000, VehicleStatus.ATIVO, null, null, java.util.Map.of());
        when(repo.existsByTenantIdAndPlateIgnoreCase(tenantId, "ABC1234")).thenReturn(true);

        assertThrows(PlateAlreadyUsedException.class, () -> service.create(principal, req));
        verify(repo, times(0)).save(any());
    }

    @Test
    void naoEnxergaVeiculoDeOutroTenant() {
        UUID vehicleId = UUID.randomUUID();
        // O veículo existe, mas pertence a outro tenant: a query com o tenantId do
        // principal não o encontra (é exatamente isso que o repositório real garante).
        when(repo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(principal, vehicleId));
    }

    @Test
    void listaApenasVeiculosDoProprioTenant() {
        Vehicle v = new Vehicle(tenantId, "XYZ9988", "Fiat", "Strada", 2021, 500);
        when(repo.findAllByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(v));

        List<VehicleResponse> result = service.list(principal);

        assertEquals(1, result.size());
        assertEquals("XYZ9988", result.get(0).plate());
        verify(repo, times(0)).findAllByTenantIdOrderByCreatedAtDesc(eq(otherTenantId));
    }

    @Test
    void atualizaERejeitaPlacaDuplicadaDeOutroVeiculo() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle existing = new Vehicle(tenantId, "OLD0001", "VW", "Gol", 2019, 2000);
        when(repo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(existing));
        when(repo.existsByTenantIdAndPlateIgnoreCaseAndIdNot(tenantId, "NEW0001", vehicleId))
                .thenReturn(true);

        VehicleRequest req = new VehicleRequest("NEW0001", "VW", "Gol", 2019, 2500, VehicleStatus.ATIVO, null, null, java.util.Map.of());

        assertThrows(PlateAlreadyUsedException.class, () -> service.update(principal, vehicleId, req));
    }

    @Test
    void deleteSoRemoveVeiculoDoProprioTenant() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle existing = new Vehicle(tenantId, "DEL0001", "Fiat", "Toro", 2020, 3000);
        when(repo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(existing));

        service.delete(principal, vehicleId);

        verify(repo).delete(existing);
    }

    @Test
    void alertaManutencaoQuandoDataProxima() {
        Vehicle v = new Vehicle(tenantId, "MNT0001", "Fiat", "Strada", 2022, 1000);
        v.update("MNT0001", "Fiat", "Strada", 2022, 1000, VehicleStatus.ATIVO,
                LocalDate.now().plusDays(5), null, java.util.Map.of());
        when(repo.findAllByTenantIdWithManutencaoAgendada(tenantId)).thenReturn(List.of(v));

        List<VehicleMaintenanceAlertResponse> alerts = service.maintenanceDue(principal);

        assertEquals(1, alerts.size());
        assertEquals("MNT0001", alerts.get(0).plate());
        assertEquals(5L, alerts.get(0).diasRestantes());
    }

    @Test
    void alertaManutencaoQuandoKmProximo() {
        Vehicle v = new Vehicle(tenantId, "MNT0002", "Fiat", "Strada", 2022, 9500);
        v.update("MNT0002", "Fiat", "Strada", 2022, 9500, VehicleStatus.ATIVO, null, 10000, java.util.Map.of());
        when(repo.findAllByTenantIdWithManutencaoAgendada(tenantId)).thenReturn(List.of(v));

        List<VehicleMaintenanceAlertResponse> alerts = service.maintenanceDue(principal);

        assertEquals(1, alerts.size());
        assertEquals(500, alerts.get(0).kmRestante());
    }

    @Test
    void naoAlertaQuandoManutencaoDistanteNoTempoENoKm() {
        Vehicle v = new Vehicle(tenantId, "MNT0003", "Fiat", "Strada", 2022, 1000);
        v.update("MNT0003", "Fiat", "Strada", 2022, 1000, VehicleStatus.ATIVO,
                LocalDate.now().plusDays(90), 20000, java.util.Map.of());
        when(repo.findAllByTenantIdWithManutencaoAgendada(tenantId)).thenReturn(List.of(v));

        List<VehicleMaintenanceAlertResponse> alerts = service.maintenanceDue(principal);

        assertEquals(0, alerts.size());
    }

    @Test
    void alertaManutencaoIncluiVencidas() {
        Vehicle v = new Vehicle(tenantId, "MNT0004", "Fiat", "Strada", 2022, 1000);
        v.update("MNT0004", "Fiat", "Strada", 2022, 1000, VehicleStatus.ATIVO,
                LocalDate.now().minusDays(3), null, java.util.Map.of());
        when(repo.findAllByTenantIdWithManutencaoAgendada(tenantId)).thenReturn(List.of(v));

        List<VehicleMaintenanceAlertResponse> alerts = service.maintenanceDue(principal);

        assertEquals(1, alerts.size());
        assertEquals(-3L, alerts.get(0).diasRestantes());
    }
}
