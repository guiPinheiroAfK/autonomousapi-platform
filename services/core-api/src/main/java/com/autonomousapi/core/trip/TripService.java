package com.autonomousapi.core.trip;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.TripStateConflictException;
import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.geo.dto.GpsPingRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.dto.StartTripRequest;
import com.autonomousapi.core.trip.dto.SubmitPingBatchResponse;
import com.autonomousapi.core.trip.dto.SubmitPingRequest;
import com.autonomousapi.core.trip.dto.TripResponse;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de viagem do motorista (spec 03). O trip em si é só a sessão (início/fim); os
 * pings brutos de GPS são encaminhados pro geo-api (schema geo) via GeoApiClient — core-api
 * nunca guarda o ping em si, só a sessão de viagem (spec 01: dado bruto de GPS é do geo-api).
 */
@Service
public class TripService {

    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final GeoApiClient geoApiClient;

    public TripService(TripRepository trips, VehicleRepository vehicles, GeoApiClient geoApiClient) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.geoApiClient = geoApiClient;
    }

    @Transactional
    public TripResponse start(JwtPrincipal principal, StartTripRequest req) {
        UUID tenantId = principal.tenantId();
        vehicles.findByIdAndTenantId(req.vehicleId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));

        if (trips.existsByTenantIdAndUserIdAndStatus(tenantId, principal.userId(), TripStatus.EM_ANDAMENTO)) {
            throw new TripStateConflictException("Já existe uma viagem em andamento — finalize-a antes de iniciar outra.");
        }

        Trip trip = new Trip(tenantId, principal.userId(), req.vehicleId());
        trips.save(trip);
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse stop(JwtPrincipal principal, UUID tripId) {
        Trip trip = findOwned(principal, tripId);
        trip.finish();
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list(JwtPrincipal principal) {
        return trips.findAllByTenantIdAndUserIdOrderByStartedAtDesc(principal.tenantId(), principal.userId())
                .stream()
                .map(TripResponse::from)
                .toList();
    }

    /** Encaminha o ping pro geo-api. Trip precisa estar em andamento e pertencer ao motorista. */
    @Transactional(readOnly = true)
    public void submitPing(JwtPrincipal principal, UUID tripId, SubmitPingRequest req) {
        Trip trip = requireOngoingTrip(principal, tripId);
        geoApiClient.ingestGpsPing(toGeoPing(trip, req));
    }

    /**
     * Esvazia um lote da fila offline do app em uma requisição só (antes era uma por ping:
     * um motorista voltando de área sem sinal disparava centenas de chamadas sequenciais).
     *
     * Processa em ordem e para no primeiro erro, devolvendo quantos entraram — o app
     * descarta esses da fila e tenta o resto depois, sem perder dado nem reenviar o que
     * já foi aceito. É também o ponto onde entra o produtor Kafka na Fase 2 (ADR 0006):
     * a assinatura do método não muda, só o destino do ping.
     */
    @Transactional(readOnly = true)
    public SubmitPingBatchResponse submitPings(
            JwtPrincipal principal, UUID tripId, List<SubmitPingRequest> pings) {
        Trip trip = requireOngoingTrip(principal, tripId);

        int accepted = 0;
        for (SubmitPingRequest req : pings) {
            try {
                geoApiClient.ingestGpsPing(toGeoPing(trip, req));
                accepted++;
            } catch (RuntimeException ex) {
                break;
            }
        }
        return new SubmitPingBatchResponse(accepted, pings.size());
    }

    private Trip requireOngoingTrip(JwtPrincipal principal, UUID tripId) {
        Trip trip = findOwned(principal, tripId);
        if (trip.getStatus() != TripStatus.EM_ANDAMENTO) {
            throw new TripStateConflictException("Viagem já finalizada — não é possível registrar novos pings.");
        }
        return trip;
    }

    private static GpsPingRequest toGeoPing(Trip trip, SubmitPingRequest req) {
        return new GpsPingRequest(
                trip.getVehicleId(), req.recordedAt(), req.lat(), req.lon(),
                req.speed(), req.heading(), req.accuracy());
    }

    private Trip findOwned(JwtPrincipal principal, UUID tripId) {
        return trips.findByIdAndTenantIdAndUserId(tripId, principal.tenantId(), principal.userId())
                .orElseThrow(() -> new NotFoundException("Viagem não encontrada."));
    }
}
