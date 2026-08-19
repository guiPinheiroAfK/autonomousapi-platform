package com.autonomousapi.core.expense.dto;

import com.autonomousapi.core.expense.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Despesa já enriquecida com os dados do veículo (quando houver), para telas que listam a
 * frota inteira (Manutenção, aba Custos). vehicleId/plate/brand/model nulos = despesa de
 * frota, sem veículo associado. Existe para evitar o padrão 1+N.
 */
public record FleetExpenseEntryResponse(
        UUID id,
        UUID vehicleId,
        String plate,
        String brand,
        String model,
        ExpenseCategory categoria,
        BigDecimal valor,
        String descricao,
        LocalDate data) {
}
