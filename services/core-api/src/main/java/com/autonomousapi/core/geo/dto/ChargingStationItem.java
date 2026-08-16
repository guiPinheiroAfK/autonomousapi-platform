package com.autonomousapi.core.geo.dto;

import java.util.UUID;

/** Resposta pública (camelCase, convenção do resto da API web) — ver GeoChargingStationItem. */
public record ChargingStationItem(
        UUID id, String name, String address, String connectorType, Double powerKw, double lat, double lon, String status) {
}
