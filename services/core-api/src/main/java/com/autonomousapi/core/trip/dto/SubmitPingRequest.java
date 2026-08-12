package com.autonomousapi.core.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Um ping de GPS enviado pelo app do motorista — fila offline no mobile garante entrega. */
public record SubmitPingRequest(
        @NotNull Instant recordedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
        Double speed,
        Double heading,
        Double accuracy) {
}
