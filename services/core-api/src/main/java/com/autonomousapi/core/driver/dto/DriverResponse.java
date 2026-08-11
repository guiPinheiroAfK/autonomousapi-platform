package com.autonomousapi.core.driver.dto;

import com.autonomousapi.core.driver.Driver;
import java.time.Instant;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String name,
        String cnh,
        String phone,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(
                d.getId(), d.getName(), d.getCnh(), d.getPhone(),
                d.getStatus().name(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
