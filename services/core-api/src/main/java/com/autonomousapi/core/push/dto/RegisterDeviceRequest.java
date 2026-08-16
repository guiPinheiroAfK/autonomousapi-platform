package com.autonomousapi.core.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDeviceRequest(
        @NotBlank String token,
        @Pattern(regexp = "ANDROID|IOS|WEB", message = "plataforma deve ser ANDROID, IOS ou WEB")
                String plataforma) {
}
