package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Espelha geo-api GET /internal/v1/driving-events (snake_case, Pydantic). Consumido só
 * internamente por {@code DriverAutoRatingJob} — nunca chega a um browser, por isso não
 * precisa do mesmo split público/interno de {@link ChargingStationItem}.
 */
public record DrivingEventsResponse(
        @JsonProperty("ping_count") int pingCount,
        @JsonProperty("hard_braking_count") int hardBrakingCount,
        @JsonProperty("overspeed_count") int overspeedCount) {

    public static DrivingEventsResponse vazio() {
        return new DrivingEventsResponse(0, 0, 0);
    }
}
