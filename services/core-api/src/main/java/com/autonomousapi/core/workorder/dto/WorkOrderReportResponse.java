package com.autonomousapi.core.workorder.dto;

import java.util.List;

public record WorkOrderReportResponse(
        List<MonthlyMaintenanceCostResponse> monthly, List<VehicleMaintenanceCostResponse> vehicleRanking) {
}
