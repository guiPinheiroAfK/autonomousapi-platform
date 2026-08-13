package com.autonomousapi.core.driver.rating;

import com.autonomousapi.core.driver.rating.dto.DriverRatingRequest;
import com.autonomousapi.core.driver.rating.dto.DriverRatingResponse;
import com.autonomousapi.core.driver.rating.dto.DriverRatingSummaryResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
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

/**
 * Avaliação manual de motorista (spec 06, item 3). TODA rota — inclusive leitura — é
 * GESTOR_FROTA/ADMIN. Diferente de DriverController (onde qualquer usuário do tenant
 * pode listar motoristas), aqui não existe rota nenhuma que um MOTORISTA autenticado
 * consiga chamar — essa é a forma de aplicar a regra "nunca exposto ao motorista".
 */
@RestController
@RequestMapping("/v1/drivers/{driverId}/ratings")
@PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
public class DriverRatingController {

    private final DriverRatingService ratingService;

    public DriverRatingController(DriverRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverRatingResponse create(
            @PathVariable UUID driverId, @Valid @RequestBody DriverRatingRequest req, Authentication auth) {
        return ratingService.create(principal(auth), driverId, req);
    }

    @GetMapping
    public List<DriverRatingResponse> list(@PathVariable UUID driverId, Authentication auth) {
        return ratingService.list(principal(auth), driverId);
    }

    @GetMapping("/summary")
    public DriverRatingSummaryResponse summary(@PathVariable UUID driverId, Authentication auth) {
        return ratingService.summary(principal(auth), driverId);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
