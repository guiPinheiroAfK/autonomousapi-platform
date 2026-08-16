package com.autonomousapi.core.routeplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** {@code stops} já vem na ordem final que o gestor confirmou na tela (pós-revisão da
 *  sugestão) — o backend não reordena de novo aqui. */
public record CreateRoutePlanRequest(UUID driverId, UUID vehicleId, @NotEmpty @Valid List<StopInput> stops) {
}
