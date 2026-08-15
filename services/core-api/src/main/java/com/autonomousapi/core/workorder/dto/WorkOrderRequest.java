package com.autonomousapi.core.workorder.dto;

import com.autonomousapi.core.workorder.WorkOrderPriority;
import com.autonomousapi.core.workorder.WorkOrderStatus;
import com.autonomousapi.core.workorder.WorkOrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkOrderRequest(
        @NotNull UUID vehicleId,
        UUID driverId,
        @NotNull WorkOrderType tipo,
        @NotNull WorkOrderStatus status,
        @NotNull WorkOrderPriority prioridade,
        @NotBlank @Size(max = 1000) String descricaoProblema,
        @Size(max = 1000) String observacoes,
        @NotBlank @Size(max = 150) String responsavelOficina,
        @NotNull LocalDate dataAbertura,
        @NotNull LocalDate previsaoConclusao,
        @NotNull @Min(0) Integer kmAbertura,
        @NotEmpty @Valid List<WorkOrderItemRequest> itens) {
}
