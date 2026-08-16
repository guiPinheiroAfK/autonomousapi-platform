package com.autonomousapi.core.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Aviso do gestor pro motorista (spec 07, item 5; ADR 0016). */
public record NotifyDriverRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String body) {
}
