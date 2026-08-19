package com.autonomousapi.core.budget.dto;

import com.autonomousapi.core.budget.Budget;
import com.autonomousapi.core.expense.ExpenseCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * {@code percentualConsumido} é derivado na leitura (nunca persistido) — mesmo espírito de
 * "nunca calcular em tempo real só quando é caro" já usado no repo: aqui é uma divisão, não
 * uma agregação cara.
 */
public record BudgetResponse(
        UUID id,
        UUID vehicleId,
        ExpenseCategory categoria,
        String periodo,
        BigDecimal valorLimite,
        BigDecimal valorConsumido,
        BigDecimal percentualConsumido) {

    public static BudgetResponse from(Budget b, BigDecimal valorConsumido) {
        BigDecimal percentual = b.getValorLimite().signum() == 0
                ? BigDecimal.ZERO
                : valorConsumido
                        .divide(b.getValorLimite(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
        return new BudgetResponse(
                b.getId(), b.getVehicleId(), b.getCategoria(), b.getPeriodo(),
                b.getValorLimite(), valorConsumido, percentual);
    }
}
