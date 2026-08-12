package com.autonomousapi.core.vehicle.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * diasRestantes/kmRestante negativos = já venceu (overdue). Um dos dois pares
 * (data ou km) pode vir nulo se o veículo só tem um dos dois agendado.
 */
public record VehicleMaintenanceAlertResponse(
        UUID vehicleId,
        String plate,
        String brand,
        String model,
        LocalDate proximaManutencaoData,
        Long diasRestantes,
        Integer proximaManutencaoKm,
        Integer kmRestante) {
}
