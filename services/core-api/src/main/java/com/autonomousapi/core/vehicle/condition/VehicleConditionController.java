package com.autonomousapi.core.vehicle.condition;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.condition.dto.VehicleConditionScoreResponse;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentRequest;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sinistro e condição do veículo (spec 06, item 2). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/vehicles/{vehicleId}")
public class VehicleConditionController {

    private final VehicleConditionService conditionService;

    public VehicleConditionController(VehicleConditionService conditionService) {
        this.conditionService = conditionService;
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_FROTA_ESCREVER')")
    public VehicleIncidentResponse registerIncident(
            @PathVariable UUID vehicleId, @Valid @RequestBody VehicleIncidentRequest req, Authentication auth) {
        return conditionService.registerIncident(principal(auth), vehicleId, req);
    }

    @PreAuthorize("hasAuthority('PERM_FROTA_VER')")
    @GetMapping("/incidents")
    public List<VehicleIncidentResponse> listIncidents(@PathVariable UUID vehicleId, Authentication auth) {
        return conditionService.listIncidents(principal(auth), vehicleId);
    }

    @DeleteMapping("/incidents/{incidentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_FROTA_ESCREVER')")
    public void deleteIncident(@PathVariable UUID vehicleId, @PathVariable UUID incidentId, Authentication auth) {
        conditionService.deleteIncident(principal(auth), vehicleId, incidentId);
    }

    @PreAuthorize("hasAuthority('PERM_FROTA_VER')")
    @GetMapping("/condition-score")
    public VehicleConditionScoreResponse score(@PathVariable UUID vehicleId, Authentication auth) {
        return conditionService.score(principal(auth), vehicleId);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
