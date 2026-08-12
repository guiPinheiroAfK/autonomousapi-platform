package com.autonomousapi.core.driver.dto;

import java.time.LocalDate;
import java.util.UUID;

/** diasRestantes negativo = CNH já vencida. */
public record DriverLicenseAlertResponse(
        UUID driverId,
        String name,
        LocalDate cnhValidade,
        long diasRestantes) {
}
