package com.autonomousapi.core.vehicle.marketvalue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VehicleMarketValueRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal valorFipe,
        @NotNull LocalDate dataReferencia,
        @Size(max = 20) String codigoFipe) {
}
