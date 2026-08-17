package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RouteStop;
import com.autonomousapi.core.routeplan.StopType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record RouteStopResponse(
        UUID id,
        StopType tipo,
        String label,
        Double lat,
        Double lon,
        UUID collectionPointId,
        @Schema(type = "string", example = "08:00:00") LocalTime janelaInicio,
        @Schema(type = "string", example = "18:00:00") LocalTime janelaFim,
        int ordemSugerida,
        Integer ordemRealExecutada,
        Instant concluidaEm) {

    public static RouteStopResponse from(RouteStop s) {
        return new RouteStopResponse(
                s.getId(),
                s.getTipo(),
                s.getLabel(),
                s.getLat(),
                s.getLon(),
                s.getCollectionPointId(),
                s.getJanelaInicio(),
                s.getJanelaFim(),
                s.getOrdemSugerida(),
                s.getOrdemRealExecutada(),
                s.getConcluidaEm());
    }
}
