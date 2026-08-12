package com.autonomousapi.core.trip;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.dto.StartTripRequest;
import com.autonomousapi.core.trip.dto.SubmitPingBatchRequest;
import com.autonomousapi.core.trip.dto.SubmitPingBatchResponse;
import com.autonomousapi.core.trip.dto.SubmitPingRequest;
import com.autonomousapi.core.trip.dto.TripResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro de viagem do motorista (spec 03, app mobile). Só o próprio motorista opera as viagens dele. */
@RestController
@RequestMapping("/v1/trips")
@PreAuthorize("hasRole('MOTORISTA')")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse start(@Valid @RequestBody StartTripRequest req, Authentication auth) {
        return tripService.start(principal(auth), req);
    }

    @GetMapping
    public List<TripResponse> list(Authentication auth) {
        return tripService.list(principal(auth));
    }

    @PostMapping("/{id}/stop")
    public TripResponse stop(@PathVariable UUID id, Authentication auth) {
        return tripService.stop(principal(auth), id);
    }

    @PostMapping("/{id}/pings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submitPing(
            @PathVariable UUID id, @Valid @RequestBody SubmitPingRequest req, Authentication auth) {
        tripService.submitPing(principal(auth), id, req);
    }

    /** Lote da fila offline do app — uma requisição em vez de uma por ping. */
    @PostMapping("/{id}/pings/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitPingBatchResponse submitPingBatch(
            @PathVariable UUID id, @Valid @RequestBody SubmitPingBatchRequest req, Authentication auth) {
        return tripService.submitPings(principal(auth), id, req.pings());
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
