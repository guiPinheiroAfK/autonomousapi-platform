package com.autonomousapi.core.driver.dto;

import com.autonomousapi.core.driver.DriverStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "CNH deve ter 11 dígitos") String cnh,
        @Size(max = 20) String phone,
        @NotNull DriverStatus status) {
}
