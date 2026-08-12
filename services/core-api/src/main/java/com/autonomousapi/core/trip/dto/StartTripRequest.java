package com.autonomousapi.core.trip.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartTripRequest(@NotNull UUID vehicleId) {
}
