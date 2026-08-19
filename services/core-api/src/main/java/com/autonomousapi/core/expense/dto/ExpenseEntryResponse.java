package com.autonomousapi.core.expense.dto;

import com.autonomousapi.core.expense.ExpenseEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseEntryResponse(
        UUID id,
        UUID vehicleId,
        String categoria,
        BigDecimal valor,
        String descricao,
        LocalDate data,
        BigDecimal litrosOuKwh,
        Integer odometro,
        Instant createdAt) {

    public static ExpenseEntryResponse from(ExpenseEntry e) {
        return new ExpenseEntryResponse(
                e.getId(), e.getVehicleId(), e.getCategoria().name(), e.getValor(),
                e.getDescricao(), e.getData(), e.getLitrosOuKwh(), e.getOdometro(), e.getCreatedAt());
    }
}
