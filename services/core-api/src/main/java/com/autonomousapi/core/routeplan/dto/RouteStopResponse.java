package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RouteStop;
import com.autonomousapi.core.routeplan.StopType;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record RouteStopResponse(
        UUID id,
        StopType tipo,
        String label,
        Double lat,
        Double lon,
        LocalTime janelaInicio,
        LocalTime janelaFim,
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
                s.getJanelaInicio(),
                s.getJanelaFim(),
                s.getOrdemSugerida(),
                s.getOrdemRealExecutada(),
                s.getConcluidaEm());
    }
}
