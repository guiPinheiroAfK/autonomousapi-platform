package com.autonomousapi.core.health;

import com.autonomousapi.core.geo.GeoApiClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health agregado exposto ao frontend. O web chama SÓ este endpoint (spec 01);
 * o core-api é quem verifica o geo-api internamente e agrega o resultado.
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    private final GeoApiClient geoApiClient;

    public HealthController(GeoApiClient geoApiClient) {
        this.geoApiClient = geoApiClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean geoUp = geoApiClient.isHealthy();

        Map<String, String> services = new LinkedHashMap<>();
        services.put("core-api", "up");
        services.put("geo-api", geoUp ? "up" : "down");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", geoUp ? "ok" : "degraded");
        body.put("services", services);
        return body;
    }
}
