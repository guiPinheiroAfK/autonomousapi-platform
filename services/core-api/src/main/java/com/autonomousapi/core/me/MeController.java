package com.autonomousapi.core.me;

import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.driver.dto.DriverProfileResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.dto.TripResponse;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentRequest;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentResponse;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Superfície do app do motorista (spec 07): tudo aqui é escopado ao motorista do token,
 * nunca a um id vindo do cliente (ADR 0013, regras de segurança não-negociáveis).
 */
@RestController
@RequestMapping("/v1/me")
@PreAuthorize("hasRole('MOTORISTA')")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/profile")
    public DriverProfileResponse profile(Authentication auth) {
        return meService.profile(principal(auth));
    }

    /** Designação ativa (null se o motorista não tiver veículo designado no momento). */
    @GetMapping("/vehicle")
    public DriverAssignmentResponse vehicle(Authentication auth) {
        return meService.vehicle(principal(auth));
    }

    @GetMapping("/vehicle/work-orders")
    public List<WorkOrderResponse> vehicleWorkOrders(Authentication auth) {
        return meService.vehicleWorkOrders(principal(auth));
    }

    @GetMapping("/trips")
    public List<TripResponse> trips(Authentication auth) {
        return meService.trips(principal(auth));
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleIncidentResponse reportIncident(
            @Valid @RequestBody VehicleIncidentRequest req, Authentication auth) {
        return meService.reportIncident(principal(auth), req);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
