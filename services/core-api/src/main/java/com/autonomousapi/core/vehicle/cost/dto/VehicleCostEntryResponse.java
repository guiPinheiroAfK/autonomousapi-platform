package com.autonomousapi.core.vehicle.cost.dto;

import com.autonomousapi.core.vehicle.cost.VehicleCostEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleCostEntryResponse(
        UUID id,
        String category,
        BigDecimal amount,
        String description,
        LocalDate occurredAt,
        Instant createdAt) {

    public static VehicleCostEntryResponse from(VehicleCostEntry e) {
        return new VehicleCostEntryResponse(
                e.getId(), e.getCategory().name(), e.getAmount(),
                e.getDescription(), e.getOccurredAt(), e.getCreatedAt());
    }
}
