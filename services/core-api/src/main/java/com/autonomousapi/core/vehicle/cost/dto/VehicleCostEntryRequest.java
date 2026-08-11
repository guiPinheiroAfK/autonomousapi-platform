package com.autonomousapi.core.vehicle.cost.dto;

import com.autonomousapi.core.vehicle.cost.VehicleCostCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VehicleCostEntryRequest(
        @NotNull VehicleCostCategory category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Size(max = 255) String description,
        @NotNull @PastOrPresent LocalDate occurredAt) {
}
