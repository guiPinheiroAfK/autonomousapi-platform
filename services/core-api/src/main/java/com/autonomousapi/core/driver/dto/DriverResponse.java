package com.autonomousapi.core.driver.dto;

import com.autonomousapi.core.driver.Driver;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String name,
        String cnh,
        String phone,
        String status,
        LocalDate cnhValidade,
        String email,
        /** True quando o motorista já aceitou o convite e tem login (ADR 0013). */
        boolean hasLogin,
        Instant createdAt,
        Instant updatedAt) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(
                d.getId(), d.getName(), d.getCnh(), d.getPhone(),
                d.getStatus().name(), d.getCnhValidade(), d.getEmail(), d.hasLogin(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
