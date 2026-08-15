package com.autonomousapi.core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentDriverResolverTest {

    private final DriverRepository drivers = mock(DriverRepository.class);
    private final CurrentDriverResolver resolver = new CurrentDriverResolver(drivers);

    @Test
    void resolveODriverVinculadoAoUsuarioDoToken() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Driver d = new Driver(tenantId, "João", "12345678901", null);
        d.linkAppUser(userId);
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.of(d));

        Driver resolved = resolver.resolve(new JwtPrincipal(userId, tenantId, "MOTORISTA"));

        assertEquals(d.getId(), resolved.getId());
    }

    @Test
    void rejeitaSemDriverVinculado() {
        UUID userId = UUID.randomUUID();
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> resolver.resolve(new JwtPrincipal(userId, UUID.randomUUID(), "MOTORISTA")));
    }

    /**
     * Defesa em profundidade: mesmo que um app_user_id colidisse entre tenants (não deveria
     * acontecer, é FK + unique), o resolver nunca devolve um driver de outro tenant.
     */
    @Test
    void rejeitaDriverDeOutroTenantMesmoComAppUserIdBatendo() {
        UUID userId = UUID.randomUUID();
        UUID tenantDoDriver = UUID.randomUUID();
        UUID tenantDoToken = UUID.randomUUID();
        Driver d = new Driver(tenantDoDriver, "João", "12345678901", null);
        d.linkAppUser(userId);
        when(drivers.findByAppUserId(userId)).thenReturn(Optional.of(d));

        assertThrows(NotFoundException.class,
                () -> resolver.resolve(new JwtPrincipal(userId, tenantDoToken, "MOTORISTA")));
    }
}
