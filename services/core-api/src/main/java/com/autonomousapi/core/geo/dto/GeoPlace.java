package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Espelha geo-api GET /internal/v1/geocode (snake_case, Pydantic). */
public record GeoPlace(@JsonProperty("display_name") String displayName, double lat, double lon) {

    public PlaceResponse toPublic() {
        return new PlaceResponse(displayName, lat, lon);
    }
}
