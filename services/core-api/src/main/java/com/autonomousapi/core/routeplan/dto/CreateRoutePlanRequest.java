package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RouteCategoria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code stops} já vem na ordem final que o gestor confirmou na tela (pós-revisão da
 *  sugestão) — o backend não reordena de novo aqui. {@code categoria} default ROTA no
 *  front; TRANSFER exige exatamente 2 stops (validado no service). {@code viagemId}
 *  (spec 13) é opcional — o front gera o UUID na ida (viagem de ida e volta marcada) e
 *  reenvia o mesmo valor ao criar a volta; o backend só armazena, nunca gera sozinho. */
public record CreateRoutePlanRequest(
        UUID driverId,
        UUID vehicleId,
        @NotNull RouteCategoria categoria,
        @NotNull LocalDate dataExecucao,
        BigDecimal valor,
        @NotEmpty @Valid List<StopInput> stops,
        UUID viagemId) {
}
