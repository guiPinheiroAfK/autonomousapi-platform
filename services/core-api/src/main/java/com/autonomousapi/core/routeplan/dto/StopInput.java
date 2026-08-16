package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.StopType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** Uma parada como o gestor a monta na tela — usado tanto em {@code suggest-order}
 *  (stateless) quanto em {@code create} (persiste). */
public record StopInput(
        @NotNull StopType tipo,
        @NotNull String label,
        @NotNull Double lat,
        @NotNull Double lon,
        LocalTime janelaInicio,
        LocalTime janelaFim) {
}
