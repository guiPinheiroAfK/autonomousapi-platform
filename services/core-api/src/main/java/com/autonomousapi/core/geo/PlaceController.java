package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.PlaceResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca de endereço (geocodificação) para alimentar o roteamento — ver
 * {@link RouteController}. Separado dele porque é capacidade própria: quem for montar
 * plano de rota com várias paradas (spec 02) usa a mesma busca.
 */
@RestController
@RequestMapping("/v1/places")
public class PlaceController {

    private final GeoApiClient geoApiClient;

    public PlaceController(GeoApiClient geoApiClient) {
        this.geoApiClient = geoApiClient;
    }

    @PreAuthorize("hasAuthority('PERM_ROTAS_VER')")
    @GetMapping("/search")
    public List<PlaceResponse> search(@RequestParam String q) {
        return geoApiClient.geocode(q);
    }
}
