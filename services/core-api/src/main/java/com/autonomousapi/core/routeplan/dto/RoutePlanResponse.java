package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RouteCategoria;
import com.autonomousapi.core.routeplan.RoutePlan;
import com.autonomousapi.core.routeplan.RoutePlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoutePlanResponse(
        UUID id,
        UUID driverId,
        String driverName,
        UUID vehicleId,
        String vehiclePlate,
        RoutePlanStatus status,
        RouteCategoria categoria,
        LocalDate dataExecucao,
        BigDecimal valor,
        Instant createdAt,
        List<RouteStopResponse> stops) {

    public static RoutePlanResponse from(
            RoutePlan p, String driverName, String vehiclePlate, List<RouteStopResponse> stops) {
        return new RoutePlanResponse(
                p.getId(), p.getDriverId(), driverName, p.getVehicleId(), vehiclePlate, p.getStatus(),
                p.getCategoria(), p.getDataExecucao(), p.getValor(), p.getCreatedAt(), stops);
    }
}
