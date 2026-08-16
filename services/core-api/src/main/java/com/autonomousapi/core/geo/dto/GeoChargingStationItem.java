package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Espelha geo-api GET /internal/v1/charging-stations (snake_case, Pydantic) — usado só
 * para desserializar a resposta dentro de {@code GeoApiClient}. O que chega ao browser é
 * {@link ChargingStationItem} (camelCase, convenção do resto da API web).
 */
public record GeoChargingStationItem(
        UUID id,
        String name,
        String address,
        @JsonProperty("connector_type") String connectorType,
        @JsonProperty("power_kw") Double powerKw,
        double lat,
        double lon,
        String status) {

    public ChargingStationItem toPublic() {
        return new ChargingStationItem(id, name, address, connectorType, powerKw, lat, lon, status);
    }
}
