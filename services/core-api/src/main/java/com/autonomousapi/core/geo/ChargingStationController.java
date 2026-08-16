package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.ChargingStationsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recarga elétrica (spec 06, item 1). Qualquer usuário autenticado pode consultar — gestor
 * planejando manutenção da frota elétrica ou motorista planejando a própria rota, os dois
 * fazem sentido (diferente de driver_rating, que é estritamente gestor-only).
 */
@RestController
@RequestMapping("/v1/charging-stations")
public class ChargingStationController {

    private final GeoApiClient geoApiClient;

    public ChargingStationController(GeoApiClient geoApiClient) {
        this.geoApiClient = geoApiClient;
    }

    @GetMapping
    public ChargingStationsResponse list(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Double radiusKm) {
        return geoApiClient.chargingStations(lat, lon, radiusKm);
    }
}
