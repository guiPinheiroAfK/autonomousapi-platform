package com.autonomousapi.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SelectTenantRequest(@NotBlank String pendingToken, @NotNull UUID tenantId) {
}
