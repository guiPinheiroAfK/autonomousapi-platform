package com.autonomousapi.core.driver.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Designa um veículo ao motorista (ADR 0014). */
public record AssignVehicleRequest(@NotNull UUID vehicleId) {
}
