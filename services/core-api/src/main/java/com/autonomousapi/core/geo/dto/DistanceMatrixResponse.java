package com.autonomousapi.core.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Espelha geo-api POST /internal/v1/table (snake_case, Pydantic). Consumido só internamente
 * pelo solver VRP ({@code RouteMatrixService}) — nunca chega a um browser, mesmo padrão de
 * {@link DrivingEventsResponse}.
 */
public record DistanceMatrixResponse(
        boolean available,
        @JsonProperty("distances_m") List<List<Double>> distancesM,
        @JsonProperty("durations_s") List<List<Double>> durationsS,
        @JsonProperty("unavailable_reason") String unavailableReason) {

    public static DistanceMatrixResponse indisponivel(String motivo) {
        return new DistanceMatrixResponse(false, List.of(), List.of(), motivo);
    }
}
