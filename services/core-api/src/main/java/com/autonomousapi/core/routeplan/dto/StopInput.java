package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.StopType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Uma parada como o gestor a monta na tela — usado tanto em {@code suggest-order}
 * (stateless) quanto em {@code create} (persiste).
 *
 * <p>Quando {@code collectionPointId} vem preenchido, o servidor resolve
 * {@code label}/{@code lat}/{@code lon} a partir do {@code CollectionPoint} cadastrado —
 * nunca confia no que o cliente mandou nesses campos nesse caso (evita drift entre o
 * cadastro e a parada, e evita spoofing de coordenada). {@code janelaInicio}/{@code
 * janelaFim}, se vierem preenchidos, sobrescrevem a janela padrão do ponto só nessa
 * instância; se vierem nulos, herdam a janela padrão do ponto. Sem
 * {@code collectionPointId}, é o fluxo de endereço avulso já existente — label/lat/lon vêm
 * do cliente (resultado de busca no Nominatim).
 */
public record StopInput(
        @NotNull StopType tipo,
        String label,
        Double lat,
        Double lon,
        UUID collectionPointId,
        @Schema(type = "string", example = "08:00:00") LocalTime janelaInicio,
        @Schema(type = "string", example = "18:00:00") LocalTime janelaFim,
        /** Contato do passageiro/cliente final dessa parada (spec 14) — opcional, sempre
         *  um {@code Passenger} já existente (o front cria na hora se for contato novo,
         *  então aqui já chega como referência, nunca nome/telefone crus). */
        UUID passengerId) {
}
