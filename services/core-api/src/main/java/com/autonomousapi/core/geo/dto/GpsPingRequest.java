package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Espelha o corpo esperado por geo-api POST /internal/v1/gps/pings (snake_case, Pydantic). */
public record GpsPingRequest(
        @JsonProperty("vehicle_id") UUID vehicleId,
        @JsonProperty("recorded_at") Instant recordedAt,
        double lat,
        double lon,
        Double speed,
        Double heading,
        Double accuracy) {
}
