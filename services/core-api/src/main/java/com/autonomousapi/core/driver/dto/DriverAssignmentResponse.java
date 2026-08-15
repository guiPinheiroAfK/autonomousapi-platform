package com.autonomousapi.core.driver.dto;

import com.autonomousapi.core.driver.DriverVehicleAssignment;
import com.autonomousapi.core.vehicle.Vehicle;
import java.time.Instant;
import java.util.UUID;

/** Designação ativa com dados do veículo para exibição (ADR 0014). */
public record DriverAssignmentResponse(
        UUID id,
        UUID driverId,
        UUID vehicleId,
        String plate,
        String brand,
        String model,
        Instant startedAt) {

    public static DriverAssignmentResponse from(DriverVehicleAssignment a, Vehicle v) {
        return new DriverAssignmentResponse(
                a.getId(), a.getDriverId(), a.getVehicleId(),
                v.getPlate(), v.getBrand(), v.getModel(), a.getStartedAt());
    }
}
