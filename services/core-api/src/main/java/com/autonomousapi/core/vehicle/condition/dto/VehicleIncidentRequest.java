package com.autonomousapi.core.vehicle.condition.dto;

import com.autonomousapi.core.vehicle.condition.IncidentSeverity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VehicleIncidentRequest(
        @NotNull LocalDate data,
        @NotNull IncidentSeverity severidade,
        @Size(max = 500) String descricao,
        @DecimalMin(value = "0.0") BigDecimal custoReparo) {
}
