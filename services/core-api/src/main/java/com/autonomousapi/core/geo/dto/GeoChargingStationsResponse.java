package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Espelha geo-api GET /internal/v1/charging-stations (snake_case, Pydantic) — ver
 * {@link GeoChargingStationItem}.
 */
public record GeoChargingStationsResponse(
        @JsonProperty("provider_available") boolean providerAvailable, List<GeoChargingStationItem> stations) {

    public ChargingStationsResponse toPublic() {
        List<ChargingStationItem> items =
                stations == null ? List.of() : stations.stream().map(GeoChargingStationItem::toPublic).toList();
        return new ChargingStationsResponse(providerAvailable, items);
    }
}
