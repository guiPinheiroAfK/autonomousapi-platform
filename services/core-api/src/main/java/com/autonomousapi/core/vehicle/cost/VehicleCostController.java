package com.autonomousapi.core.vehicle.cost;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryRequest;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryResponse;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostSummaryResponse;
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

/** Custo por km de um veículo (spec 05, Fase 1). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/vehicles/{vehicleId}")
public class VehicleCostController {

    private final VehicleCostService costService;

    public VehicleCostController(VehicleCostService costService) {
        this.costService = costService;
    }

    @PostMapping("/costs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public VehicleCostEntryResponse addEntry(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleCostEntryRequest req,
            Authentication auth) {
        return costService.addEntry(principal(auth), vehicleId, req);
    }

    @GetMapping("/costs")
    public List<VehicleCostEntryResponse> list(@PathVariable UUID vehicleId, Authentication auth) {
        return costService.list(principal(auth), vehicleId);
    }

    @GetMapping("/cost-summary")
    public VehicleCostSummaryResponse summary(@PathVariable UUID vehicleId, Authentication auth) {
        return costService.summary(principal(auth), vehicleId);
    }

    @DeleteMapping("/costs/{costId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void delete(
            @PathVariable UUID vehicleId, @PathVariable UUID costId, Authentication auth) {
        costService.delete(principal(auth), vehicleId, costId);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
