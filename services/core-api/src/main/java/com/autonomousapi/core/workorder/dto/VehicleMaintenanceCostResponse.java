package com.autonomousapi.core.workorder.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleMaintenanceCostResponse(UUID vehicleId, String plate, String vehicleName, BigDecimal total) {
}
