package com.autonomousapi.core.vehicle.dto;

import com.autonomousapi.core.vehicle.VehicleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 10) String plate,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 80) String model,
        Integer modelYear,
        @NotNull @Min(0) Integer odometerKm,
        @NotNull VehicleStatus status) {
}
