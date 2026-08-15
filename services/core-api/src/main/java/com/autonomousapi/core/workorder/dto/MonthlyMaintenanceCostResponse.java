package com.autonomousapi.core.workorder.dto;

import java.math.BigDecimal;

public record MonthlyMaintenanceCostResponse(
        String mes,
        BigDecimal custoPreventiva,
        BigDecimal custoCorretiva,
        BigDecimal custoRevisao,
        BigDecimal custoSinistro,
        long qtdOS) {
}
