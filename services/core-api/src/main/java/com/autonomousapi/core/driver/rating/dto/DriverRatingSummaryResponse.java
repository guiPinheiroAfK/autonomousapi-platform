package com.autonomousapi.core.driver.rating.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DriverRatingSummaryResponse(UUID driverId, BigDecimal notaMedia, int totalAvaliacoes) {

    public static DriverRatingSummaryResponse vazio(UUID driverId) {
        return new DriverRatingSummaryResponse(driverId, null, 0);
    }
}
