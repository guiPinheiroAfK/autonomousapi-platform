package com.autonomousapi.core.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code odometerKm} é o odômetro total do veículo (o que está no painel dele hoje).
 * {@code kmRodado} é a distância percorrida desde que o veículo foi cadastrado na
 * plataforma ({@code odometerKm - odometroInicial}, migration V24) — é sobre esse número
 * que {@code custoPorKm} é calculado, não sobre o odômetro total, senão km rodado antes
 * do cadastro (ex.: carro usado com 60.000 km de uso anterior) inflaria o denominador e
 * mostraria um custo por km artificialmente baixo.
 *
 * custoPorKm é null quando kmRodado é 0 ou negativo (nenhum km rastreado desde o
 * cadastro ainda) — evita divisão por zero em vez de devolver um valor enganoso.
 */
public record ExpenseSummaryResponse(
        UUID vehicleId,
        BigDecimal totalValor,
        int odometerKm,
        int kmRodado,
        BigDecimal custoPorKm) {
}
