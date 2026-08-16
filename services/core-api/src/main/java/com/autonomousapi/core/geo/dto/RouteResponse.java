package com.autonomousapi.core.geo.dto;

import java.util.List;

/** Resposta pública (camelCase, convenção do resto da API web) — ver GeoRouteResponse. */
public record RouteResponse(
        boolean available,
        Double distanceM,
        Double durationS,
        /** [[lon, lat], ...] — ordem GeoJSON, que é o que biblioteca de mapa espera. */
        List<List<Double>> geometry,
        List<RouteStep> steps,
        String unavailableReason) {

    public record RouteStep(String instructionType, String modifier, String name, double distanceM, double durationS) {}

    public static RouteResponse indisponivel(String motivo) {
        return new RouteResponse(false, null, null, List.of(), List.of(), motivo);
    }
}
