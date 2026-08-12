package com.autonomousapi.core.vehicle.dto;

import com.autonomousapi.core.vehicle.Vehicle;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String plate,
        String brand,
        String model,
        Integer modelYear,
        int odometerKm,
        String status,
        LocalDate proximaManutencaoData,
        Integer proximaManutencaoKm,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(),
                v.getPlate(),
                v.getBrand(),
                v.getModel(),
                v.getModelYear(),
                v.getOdometerKm(),
                v.getStatus().name(),
                v.getProximaManutencaoData(),
                v.getProximaManutencaoKm(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }
}
