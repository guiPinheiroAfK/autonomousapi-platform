package com.autonomousapi.core.budget.dto;

import com.autonomousapi.core.expense.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code vehicleId} e {@code categoria} são opcionais (orçamento de frota / de todas as categorias). */
public record BudgetRequest(
        UUID vehicleId,
        ExpenseCategory categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valorLimite) {
}
