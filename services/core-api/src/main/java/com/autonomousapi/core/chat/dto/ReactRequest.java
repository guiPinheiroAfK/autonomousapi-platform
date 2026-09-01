package com.autonomousapi.core.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactRequest(@NotBlank @Size(max = 8) String emoji) {
}
