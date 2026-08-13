package com.autonomousapi.core.vehicle.marketvalue;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.marketvalue.dto.VehicleMarketValueRequest;
import com.autonomousapi.core.vehicle.marketvalue.dto.VehicleMarketValueResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Valor de mercado/FIPE (spec 06, item 2). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/vehicles/{vehicleId}/market-value")
public class VehicleMarketValueController {

    private final VehicleMarketValueService marketValueService;

    public VehicleMarketValueController(VehicleMarketValueService marketValueService) {
        this.marketValueService = marketValueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public VehicleMarketValueResponse record(
            @PathVariable UUID vehicleId, @Valid @RequestBody VehicleMarketValueRequest req, Authentication auth) {
        return marketValueService.record(principal(auth), vehicleId, req);
    }

    @GetMapping
    public ResponseEntity<VehicleMarketValueResponse> latest(@PathVariable UUID vehicleId, Authentication auth) {
        return marketValueService.latest(principal(auth), vehicleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
