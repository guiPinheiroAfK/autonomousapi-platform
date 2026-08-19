package com.autonomousapi.core.expense.dto;

import java.math.BigDecimal;

/** Soma de despesas de um mês (formato "yyyy-MM") em toda a frota do tenant. */
public record MonthlyCostResponse(String month, BigDecimal total) {
}
