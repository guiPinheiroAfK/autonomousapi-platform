package com.autonomousapi.core.vehicle.cost.dto;

import java.math.BigDecimal;

/** Soma de custos de um mês (formato "yyyy-MM") em toda a frota do tenant. */
public record MonthlyCostResponse(String month, BigDecimal total) {
}
