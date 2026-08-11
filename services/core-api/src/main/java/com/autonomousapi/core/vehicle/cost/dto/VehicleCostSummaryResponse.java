package com.autonomousapi.core.vehicle.cost.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * costPerKm é null quando o odômetro do veículo é 0 (sem km rodado registrado ainda) —
 * evita divisão por zero em vez de devolver um valor enganoso.
 */
public record VehicleCostSummaryResponse(
        UUID vehicleId,
        BigDecimal totalCost,
        int odometerKm,
        BigDecimal costPerKm) {
}
