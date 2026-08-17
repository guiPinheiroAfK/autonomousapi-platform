package com.autonomousapi.core.collectionpoint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record CollectionPointRequest(
        @NotBlank String nome,
        @NotBlank String endereco,
        @NotNull Double lat,
        @NotNull Double lon,
        @Schema(type = "string", example = "08:00:00") LocalTime janelaInicio,
        @Schema(type = "string", example = "18:00:00") LocalTime janelaFim) {
}
