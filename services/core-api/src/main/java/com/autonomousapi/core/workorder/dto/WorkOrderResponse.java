package com.autonomousapi.core.workorder.dto;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.workorder.WorkOrder;
import com.autonomousapi.core.workorder.WorkOrderItem;
import com.autonomousapi.core.workorder.WorkOrderPriority;
import com.autonomousapi.core.workorder.WorkOrderStatus;
import com.autonomousapi.core.workorder.WorkOrderType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * vehiclePlate/vehicleName/driverName vêm resolvidos pelo service (mapa em memória, não
 * uma query por OS) — mesma técnica do FleetCostEntryResponse, evita N+1 na lista.
 */
public record WorkOrderResponse(
        UUID id,
        String numero,
        UUID vehicleId,
        String vehiclePlate,
        String vehicleName,
        UUID driverId,
        String driverName,
        WorkOrderType tipo,
        WorkOrderStatus status,
        WorkOrderPriority prioridade,
        String descricaoProblema,
        String observacoes,
        String responsavelOficina,
        LocalDate dataAbertura,
        LocalDate previsaoConclusao,
        int kmAbertura,
        List<WorkOrderItemResponse> itens,
        BigDecimal custoTotal) {

    public static WorkOrderResponse from(WorkOrder wo, List<WorkOrderItem> itens, Vehicle vehicle, Driver driver) {
        List<WorkOrderItemResponse> itensResponse = itens.stream().map(WorkOrderItemResponse::from).toList();
        BigDecimal total = itens.stream().map(WorkOrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkOrderResponse(
                wo.getId(), wo.getNumero(), wo.getVehicleId(),
                vehicle != null ? vehicle.getPlate() : null,
                vehicle != null ? vehicle.getBrand() + " " + vehicle.getModel() : null,
                wo.getDriverId(),
                driver != null ? driver.getName() : null,
                wo.getTipo(), wo.getStatus(), wo.getPrioridade(), wo.getDescricaoProblema(), wo.getObservacoes(),
                wo.getResponsavelOficina(), wo.getDataAbertura(), wo.getPrevisaoConclusao(), wo.getKmAbertura(),
                itensResponse, total);
    }
}
