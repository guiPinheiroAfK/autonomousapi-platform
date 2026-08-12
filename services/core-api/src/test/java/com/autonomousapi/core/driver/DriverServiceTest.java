package com.autonomousapi.core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.dto.DriverLicenseAlertResponse;
import com.autonomousapi.core.driver.dto.DriverRequest;
import com.autonomousapi.core.driver.dto.DriverResponse;
import com.autonomousapi.core.error.CnhAlreadyUsedException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverServiceTest {

    private final DriverRepository repo = mock(DriverRepository.class);
    private final DriverService service = new DriverService(repo);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal =
            new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void criaMotoristaNoTenantDoPrincipal() {
        DriverRequest req = new DriverRequest("João Silva", "12345678901", "11999990000", DriverStatus.ATIVO, null);
        when(repo.existsByTenantIdAndCnh(tenantId, "12345678901")).thenReturn(false);

        DriverResponse resp = service.create(principal, req);

        assertEquals("João Silva", resp.name());
        verify(repo).save(any(Driver.class));
    }

    @Test
    void rejeitaCnhDuplicadaNoMesmoTenant() {
        DriverRequest req = new DriverRequest("João Silva", "12345678901", null, DriverStatus.ATIVO, null);
        when(repo.existsByTenantIdAndCnh(tenantId, "12345678901")).thenReturn(true);

        assertThrows(CnhAlreadyUsedException.class, () -> service.create(principal, req));
        verify(repo, times(0)).save(any());
    }

    @Test
    void naoEnxergaMotoristaDeOutroTenant() {
        UUID driverId = UUID.randomUUID();
        when(repo.findByIdAndTenantId(driverId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(principal, driverId));
    }

    @Test
    void deleteSoRemoveMotoristaDoProprioTenant() {
        UUID driverId = UUID.randomUUID();
        Driver existing = new Driver(tenantId, "Maria Souza", "98765432100", null);
        when(repo.findByIdAndTenantId(driverId, tenantId)).thenReturn(Optional.of(existing));

        service.delete(principal, driverId);

        verify(repo).delete(existing);
    }

    @Test
    void alertaCnhQuandoVencendoEmBreve() {
        Driver d = new Driver(tenantId, "Carlos Rocha", "11122233344", null);
        d.update("Carlos Rocha", "11122233344", null, DriverStatus.ATIVO, LocalDate.now().plusDays(10));
        when(repo.findAllByTenantIdAndCnhValidadeIsNotNull(tenantId)).thenReturn(List.of(d));

        List<DriverLicenseAlertResponse> alerts = service.licenseExpiring(principal);

        assertEquals(1, alerts.size());
        assertEquals(10L, alerts.get(0).diasRestantes());
    }

    @Test
    void naoAlertaCnhQuandoValidadeDistante() {
        Driver d = new Driver(tenantId, "Carlos Rocha", "11122233344", null);
        d.update("Carlos Rocha", "11122233344", null, DriverStatus.ATIVO, LocalDate.now().plusDays(180));
        when(repo.findAllByTenantIdAndCnhValidadeIsNotNull(tenantId)).thenReturn(List.of(d));

        List<DriverLicenseAlertResponse> alerts = service.licenseExpiring(principal);

        assertEquals(0, alerts.size());
    }
}
