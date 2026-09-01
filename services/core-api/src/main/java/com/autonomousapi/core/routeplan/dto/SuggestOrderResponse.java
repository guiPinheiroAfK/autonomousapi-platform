package com.autonomousapi.core.routeplan.dto;

import java.util.List;

/**
 * Spec 11, gap "fallback do OSRM visível pro gestor" — {@code fallbackHaversine} avisa
 * quando a ordem sugerida veio da distância em linha reta (OSRM {@code /table} fora do ar
 * ou incompleto), não da distância real de rota. Antes desta entrega essa informação existia
 * no backend ({@code RouteMatrixService.Matriz.fonte}) mas era descartada silenciosamente.
 */
public record SuggestOrderResponse(List<StopInput> stops, boolean fallbackHaversine) {
}
