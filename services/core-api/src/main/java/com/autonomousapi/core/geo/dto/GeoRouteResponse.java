package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Espelha geo-api GET /internal/v1/route (snake_case, Pydantic). Convertido para
 * {@link RouteResponse} (camelCase) antes de sair para o browser — ver
 * {@link GeoChargingStationsResponse} para o mesmo padrão.
 */
public record GeoRouteResponse(
        boolean available,
        @JsonProperty("distance_m") Double distanceM,
        @JsonProperty("duration_s") Double durationS,
        List<List<Double>> geometry,
        List<GeoRouteStep> steps,
        @JsonProperty("unavailable_reason") String unavailableReason) {

    public record GeoRouteStep(
            @JsonProperty("instruction_type") String instructionType,
            String modifier,
            String name,
            @JsonProperty("distance_m") double distanceM,
            @JsonProperty("duration_s") double durationS) {

        public RouteResponse.RouteStep toPublic() {
            return new RouteResponse.RouteStep(instructionType, modifier, name, distanceM, durationS);
        }
    }

    public RouteResponse toPublic() {
        return new RouteResponse(
                available,
                distanceM,
                durationS,
                geometry != null ? geometry : List.of(),
                steps != null ? steps.stream().map(GeoRouteStep::toPublic).toList() : List.of(),
                unavailableReason);
    }
}
