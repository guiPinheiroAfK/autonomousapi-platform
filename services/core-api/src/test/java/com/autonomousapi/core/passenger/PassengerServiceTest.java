package com.autonomousapi.core.passenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.passenger.dto.PassengerRequest;
import com.autonomousapi.core.passenger.dto.PassengerResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PassengerServiceTest {

    private final PassengerRepository passengers = mock(PassengerRepository.class);
    private final PassengerService service = new PassengerService(passengers);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void createPersisteEscopadoAoTenantDoPrincipal() {
        when(passengers.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PassengerResponse resp = service.create(principal, new PassengerRequest("Maria", "+5545999990000"));

        assertEquals("Maria", resp.nome());
        assertEquals("+5545999990000", resp.telefone());
    }

    @Test
    void updateNaoEncontradoNoTenantLancaNotFound() {
        UUID id = UUID.randomUUID();
        when(passengers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.update(principal, id, new PassengerRequest("Maria", "+5545999990000")));
    }

    @Test
    void deleteRemoveDeVerdadeSemMarcarInativo() {
        UUID id = UUID.randomUUID();
        Passenger p = new Passenger(tenantId, "Maria", "+5545999990000");
        when(passengers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        service.delete(principal, id);

        verify(passengers).delete(p);
    }

    @Test
    void listDevolveOrdenadoPorNomeEscopadoAoTenant() {
        when(passengers.findAllByTenantIdOrderByNomeAsc(tenantId))
                .thenReturn(List.of(new Passenger(tenantId, "Ana", "+5545999990001")));

        List<PassengerResponse> resp = service.list(principal);

        assertEquals(1, resp.size());
        assertEquals("Ana", resp.get(0).nome());
    }
}
