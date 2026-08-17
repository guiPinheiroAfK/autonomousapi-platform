package com.autonomousapi.core.collectionpoint.dto;

import com.autonomousapi.core.collectionpoint.CollectionPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.util.UUID;

public record CollectionPointResponse(
        UUID id,
        String nome,
        String endereco,
        Double lat,
        Double lon,
        @Schema(type = "string", example = "08:00:00") LocalTime janelaInicio,
        @Schema(type = "string", example = "18:00:00") LocalTime janelaFim,
        boolean posicaoAjustada,
        boolean ativo) {

    public static CollectionPointResponse from(CollectionPoint p) {
        return new CollectionPointResponse(
                p.getId(), p.getNome(), p.getEndereco(), p.getLat(), p.getLon(),
                p.getJanelaInicio(), p.getJanelaFim(), p.isPosicaoAjustada(), p.isAtivo());
    }
}
