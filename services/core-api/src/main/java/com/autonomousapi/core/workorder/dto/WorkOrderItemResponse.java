package com.autonomousapi.core.workorder.dto;

import com.autonomousapi.core.workorder.WorkOrderItem;
import java.math.BigDecimal;

public record WorkOrderItemResponse(String descricao, int quantidade, BigDecimal valorUnitario, BigDecimal subtotal) {

    public static WorkOrderItemResponse from(WorkOrderItem item) {
        return new WorkOrderItemResponse(item.getDescricao(), item.getQuantidade(), item.getValorUnitario(), item.subtotal());
    }
}
