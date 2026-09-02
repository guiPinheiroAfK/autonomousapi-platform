package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.RouteResponse;
import com.autonomousapi.core.pricing.RouteCostEstimator;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roteamento ponto-a-ponto (spec 02, Fase 1-2). Qualquer usuário autenticado consulta:
 * gestor planejando a operação e motorista vendo a rota sugerida são os dois casos de uso
 * previstos no roadmap ("exposto no web/mobile").
 *
 * Não persiste nada — é consulta pura ao motor de roteamento. O plano de rota com várias
 * paradas ({@code route_plan}/{@code route_stop} da spec 02) é a extensão seguinte, e aí
 * sim entra modelagem própria.
 */
@RestController
@RequestMapping("/v1/routes")
public class RouteController {

    private final GeoApiClient geoApiClient;
    private final VehicleRepository vehicles;
    private final RouteCostEstimator costEstimator;

    public RouteController(GeoApiClient geoApiClient, VehicleRepository vehicles, RouteCostEstimator costEstimator) {
        this.geoApiClient = geoApiClient;
        this.vehicles = vehicles;
        this.costEstimator = costEstimator;
    }

    /**
     * {@code vehicleId} é opcional (spec 09) — quando informado, tenta anexar
     * {@code custoEstimado}/{@code valorSugerido} à resposta. Veículo de outro tenant,
     * inexistente, ou sem os pré-requisitos de cálculo (consumo/preço de referência) apenas
     * deixa os dois campos null — nunca falha o preview por causa disso, custo é informação
     * complementar à rota, não um requisito dela.
     */
    @PreAuthorize("hasAuthority('PERM_ROTAS_VER')")
    @GetMapping("/preview")
    public RouteResponse preview(
            @RequestParam double fromLat,
            @RequestParam double fromLon,
            @RequestParam double toLat,
            @RequestParam double toLon,
            @RequestParam(required = false) UUID vehicleId,
            Authentication auth) {
        RouteResponse resposta = geoApiClient.route(fromLat, fromLon, toLat, toLon);
        if (vehicleId == null || !resposta.available() || resposta.distanceM() == null) {
            return resposta;
        }

        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        return vehicles.findByIdAndTenantId(vehicleId, principal.tenantId())
                .flatMap(vehicle -> costEstimator.estimar(principal.tenantId(), vehicle, resposta.distanceM() / 1000.0))
                .map(estimate -> resposta.comCustoEstimado(estimate.custoEstimado(), estimate.valorSugerido()))
                .orElse(resposta);
    }
}
