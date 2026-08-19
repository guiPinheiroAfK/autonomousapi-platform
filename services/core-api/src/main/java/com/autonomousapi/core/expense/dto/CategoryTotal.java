package com.autonomousapi.core.expense.dto;

import com.autonomousapi.core.expense.ExpenseCategory;
import java.math.BigDecimal;

/** Soma de despesas de uma categoria num intervalo — alimenta a "Visão geral" da aba Custos. */
public record CategoryTotal(ExpenseCategory categoria, BigDecimal total) {
}
