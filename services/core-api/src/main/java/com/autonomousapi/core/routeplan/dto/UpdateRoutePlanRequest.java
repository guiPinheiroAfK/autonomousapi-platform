package com.autonomousapi.core.routeplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spec 11, gap "edição de rota já atribuída" — só rota {@code PLANEJADA} (validado no
 * service). Diferente de {@link CreateRoutePlanRequest}: sem {@code driverId} (reatribuição
 * já tem seu próprio caminho, ADR 0021) e sem {@code categoria}/{@code viagemId} (não
 * editáveis — trocar categoria é efetivamente outra rota). {@code stops} substitui a lista
 * inteira, mesmo raciocínio de {@code create}: o gestor manda a lista final já revisada.
 */
public record UpdateRoutePlanRequest(
        UUID vehicleId,
        @NotNull LocalDate dataExecucao,
        BigDecimal valor,
        @NotEmpty @Valid List<StopInput> stops) {
}
