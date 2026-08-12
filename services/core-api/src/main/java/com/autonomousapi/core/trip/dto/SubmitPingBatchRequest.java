package com.autonomousapi.core.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lote de pings vindo da fila offline do app. O teto de 500 evita payload sem limite
 * de um motorista que ficou muitas horas sem sinal — o app manda em lotes sucessivos.
 */
public record SubmitPingBatchRequest(
        @NotEmpty @Size(max = 500) @Valid List<SubmitPingRequest> pings) {
}
