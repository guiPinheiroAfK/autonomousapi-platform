package com.autonomousapi.core.passenger.dto;

import jakarta.validation.constraints.NotBlank;

public record PassengerRequest(@NotBlank String nome, @NotBlank String telefone) {
}
