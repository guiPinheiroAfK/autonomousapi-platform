package com.autonomousapi.core.geo.dto;

import java.util.List;

/** Resposta pública (camelCase, convenção do resto da API web) — ver GeoChargingStationsResponse. */
public record ChargingStationsResponse(boolean providerAvailable, List<ChargingStationItem> stations) {

    public static ChargingStationsResponse indisponivel() {
        return new ChargingStationsResponse(false, List.of());
    }
}
