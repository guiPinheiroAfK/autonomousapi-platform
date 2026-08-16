package com.autonomousapi.core.routeplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SuggestOrderRequest(@NotEmpty @Valid List<StopInput> stops) {
}
