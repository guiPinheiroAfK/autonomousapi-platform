package com.autonomousapi.core.routeplan.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignDriverRequest(@NotNull UUID driverId) {
}
