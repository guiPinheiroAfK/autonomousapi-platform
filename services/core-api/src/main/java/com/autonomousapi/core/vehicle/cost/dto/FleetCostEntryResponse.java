package com.autonomousapi.core.vehicle.cost.dto;

import com.autonomousapi.core.vehicle.cost.VehicleCostCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento de custo já enriquecido com os dados do veículo, para telas que listam
 * a frota inteira (Manutenção). Existe para evitar o padrão 1+N: sem ele o front
 * pedia a lista de veículos e depois os custos de cada um, uma requisição por veículo.
 */
public record FleetCostEntryResponse(
        UUID id,
        UUID vehicleId,
        String plate,
        String brand,
        String model,
        VehicleCostCategory category,
        BigDecimal amount,
        String description,
        LocalDate occurredAt) {
}
