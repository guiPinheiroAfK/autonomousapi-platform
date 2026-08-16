package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.RouteResponse;
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

    public RouteController(GeoApiClient geoApiClient) {
        this.geoApiClient = geoApiClient;
    }

    @GetMapping("/preview")
    public RouteResponse preview(
            @RequestParam double fromLat,
            @RequestParam double fromLon,
            @RequestParam double toLat,
            @RequestParam double toLon) {
        return geoApiClient.route(fromLat, fromLon, toLat, toLon);
    }
}
