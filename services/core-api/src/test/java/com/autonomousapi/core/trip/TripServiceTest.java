package com.autonomousapi.core.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.TripStateConflictException;
import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.dto.StartTripRequest;
import com.autonomousapi.core.trip.dto.SubmitPingRequest;
import com.autonomousapi.core.trip.dto.TripResponse;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TripServiceTest {

    private final TripRepository tripRepo = mock(TripRepository.class);
    private final VehicleRepository vehicleRepo = mock(VehicleRepository.class);
    private final GeoApiClient geoApiClient = mock(GeoApiClient.class);
    private final TripService service = new TripService(tripRepo, vehicleRepo, geoApiClient);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

    @Test
    void iniciaViagemQuandoVeiculoExisteENaoHaViagemEmAndamento() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 1000);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(tripRepo.existsByTenantIdAndUserIdAndStatus(tenantId, principal.userId(), TripStatus.EM_ANDAMENTO))
                .thenReturn(false);

        TripResponse response = service.start(principal, new StartTripRequest(vehicleId));

        assertEquals("EM_ANDAMENTO", response.status());
        assertEquals(vehicleId, response.vehicleId());
    }

    @Test
    void naoIniciaViagemComVeiculoDeOutroTenant() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.start(principal, new StartTripRequest(vehicleId)));
    }

    @Test
    void naoIniciaSegundaViagemEnquantoUmaEstaEmAndamento() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "VW", "Saveiro", 2022, 1000);
        when(vehicleRepo.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(tripRepo.existsByTenantIdAndUserIdAndStatus(tenantId, principal.userId(), TripStatus.EM_ANDAMENTO))
                .thenReturn(true);

        assertThrows(
                TripStateConflictException.class, () -> service.start(principal, new StartTripRequest(vehicleId)));
    }

    @Test
    void encaminhaPingParaGeoApiQuandoViagemEmAndamento() {
        UUID vehicleId = UUID.randomUUID();
        Trip trip = new Trip(tenantId, principal.userId(), vehicleId);
        when(tripRepo.findByIdAndTenantIdAndUserId(trip.getId(), tenantId, principal.userId()))
                .thenReturn(Optional.of(trip));

        SubmitPingRequest ping = new SubmitPingRequest(Instant.now(), -25.5, -54.5, 40.0, 90.0, 5.0);
        service.submitPing(principal, trip.getId(), ping);

        ArgumentCaptor<com.autonomousapi.core.geo.dto.GpsPingRequest> captor =
                ArgumentCaptor.forClass(com.autonomousapi.core.geo.dto.GpsPingRequest.class);
        verify(geoApiClient).ingestGpsPing(captor.capture());
        assertEquals(vehicleId, captor.getValue().vehicleId());
    }

    @Test
    void loteDePingsEncaminhaTodosQuandoGeoApiResponde() {
        Trip trip = new Trip(tenantId, principal.userId(), UUID.randomUUID());
        when(tripRepo.findByIdAndTenantIdAndUserId(trip.getId(), tenantId, principal.userId()))
                .thenReturn(Optional.of(trip));

        var lote = List.of(
                new SubmitPingRequest(Instant.now(), -25.1, -54.1, null, null, null),
                new SubmitPingRequest(Instant.now(), -25.2, -54.2, null, null, null),
                new SubmitPingRequest(Instant.now(), -25.3, -54.3, null, null, null));

        var resposta = service.submitPings(principal, trip.getId(), lote);

        assertEquals(3, resposta.accepted());
        assertEquals(3, resposta.received());
        verify(geoApiClient, times(3)).ingestGpsPing(any());
    }

    @Test
    void loteParaNoPrimeiroErroEDevolveQuantosEntraram() {
        Trip trip = new Trip(tenantId, principal.userId(), UUID.randomUUID());
        when(tripRepo.findByIdAndTenantIdAndUserId(trip.getId(), tenantId, principal.userId()))
                .thenReturn(Optional.of(trip));
        // Primeiro ping passa, segundo falha (geo-api fora do ar no meio do lote).
        doNothing().doThrow(new RuntimeException("geo-api indisponível"))
                .when(geoApiClient).ingestGpsPing(any());

        var lote = List.of(
                new SubmitPingRequest(Instant.now(), -25.1, -54.1, null, null, null),
                new SubmitPingRequest(Instant.now(), -25.2, -54.2, null, null, null),
                new SubmitPingRequest(Instant.now(), -25.3, -54.3, null, null, null));

        var resposta = service.submitPings(principal, trip.getId(), lote);

        // O app descarta 1 da fila e mantém os outros 2 para nova tentativa.
        assertEquals(1, resposta.accepted());
        assertEquals(3, resposta.received());
        verify(geoApiClient, times(2)).ingestGpsPing(any());
    }

    @Test
    void naoEncaminhaPingDeViagemJaFinalizada() {
        UUID vehicleId = UUID.randomUUID();
        Trip trip = new Trip(tenantId, principal.userId(), vehicleId);
        trip.finish();
        when(tripRepo.findByIdAndTenantIdAndUserId(trip.getId(), tenantId, principal.userId()))
                .thenReturn(Optional.of(trip));

        SubmitPingRequest ping = new SubmitPingRequest(Instant.now(), -25.5, -54.5, null, null, null);

        assertThrows(TripStateConflictException.class, () -> service.submitPing(principal, trip.getId(), ping));
        verify(geoApiClient, never()).ingestGpsPing(any());
    }
}
