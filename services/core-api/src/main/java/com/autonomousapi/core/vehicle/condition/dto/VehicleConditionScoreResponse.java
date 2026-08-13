package com.autonomousapi.core.vehicle.condition.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleConditionScoreResponse(UUID vehicleId, BigDecimal score, String algorithmVersion) {

    /** Score cheio (100) até o primeiro sinistro registrado — não existe pontuação negativa por omissão. */
    public static VehicleConditionScoreResponse cheio(UUID vehicleId) {
        return new VehicleConditionScoreResponse(vehicleId, BigDecimal.valueOf(100), "v1-incident-penalty");
    }
}
