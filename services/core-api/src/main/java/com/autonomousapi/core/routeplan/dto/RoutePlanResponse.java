package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RoutePlan;
import com.autonomousapi.core.routeplan.RoutePlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutePlanResponse(
        UUID id,
        UUID driverId,
        String driverName,
        UUID vehicleId,
        String vehiclePlate,
        RoutePlanStatus status,
        Instant createdAt,
        List<RouteStopResponse> stops) {

    public static RoutePlanResponse from(
            RoutePlan p, String driverName, String vehiclePlate, List<RouteStopResponse> stops) {
        return new RoutePlanResponse(
                p.getId(), p.getDriverId(), driverName, p.getVehicleId(), vehiclePlate, p.getStatus(),
                p.getCreatedAt(), stops);
    }
}
