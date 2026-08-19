package com.autonomousapi.core.geo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta pública (camelCase, convenção do resto da API web) — ver GeoRouteResponse.
 * {@code custoEstimado}/{@code valorSugerido} (spec 09) só vêm preenchidos quando o
 * chamador informa {@code vehicleId} E o veículo tem consumo cadastrado E o tenant tem
 * preço de referência pro tipo de combustível dele — em qualquer outro caso ficam null,
 * nunca quebram o preview de rota (a rota em si não depende de custo).
 */
public record RouteResponse(
        boolean available,
        Double distanceM,
        Double durationS,
        /** [[lon, lat], ...] — ordem GeoJSON, que é o que biblioteca de mapa espera. */
        List<List<Double>> geometry,
        List<RouteStep> steps,
        String unavailableReason,
        BigDecimal custoEstimado,
        BigDecimal valorSugerido) {

    public record RouteStep(String instructionType, String modifier, String name, double distanceM, double durationS) {}

    public static RouteResponse indisponivel(String motivo) {
        return new RouteResponse(false, null, null, List.of(), List.of(), motivo, null, null);
    }

    public RouteResponse comCustoEstimado(BigDecimal custoEstimado, BigDecimal valorSugerido) {
        return new RouteResponse(
                available, distanceM, durationS, geometry, steps, unavailableReason, custoEstimado, valorSugerido);
    }
}
