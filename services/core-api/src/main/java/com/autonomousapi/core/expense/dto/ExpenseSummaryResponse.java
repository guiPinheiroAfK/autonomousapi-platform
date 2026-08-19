package com.autonomousapi.core.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * costPerKm é null quando o odômetro do veículo é 0 (sem km rodado registrado ainda) —
 * evita divisão por zero em vez de devolver um valor enganoso.
 */
public record ExpenseSummaryResponse(
        UUID vehicleId,
        BigDecimal totalValor,
        int odometerKm,
        BigDecimal custoPorKm) {
}
