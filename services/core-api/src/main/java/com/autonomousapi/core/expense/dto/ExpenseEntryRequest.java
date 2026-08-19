package com.autonomousapi.core.expense.dto;

import com.autonomousapi.core.expense.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code vehicleId} é opcional (despesa de frota, ex. seguro corporativo) — no endpoint
 * escopado por veículo (POST /v1/vehicles/{vehicleId}/costs) o valor daqui é ignorado, o
 * servidor sempre usa o path variable (nunca confia no corpo pra decidir o veículo).
 * {@code litrosOuKwh}/{@code odometro} só são aceitos quando categoria=COMBUSTIVEL — validado
 * em ExpenseEntryService, não só aqui (mesmo padrão de validação em profundidade do repo).
 */
public record ExpenseEntryRequest(
        UUID vehicleId,
        @NotNull ExpenseCategory categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
        @Size(max = 255) String descricao,
        @NotNull @PastOrPresent LocalDate data,
        BigDecimal litrosOuKwh,
        Integer odometro) {
}
