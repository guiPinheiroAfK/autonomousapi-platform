package com.autonomousapi.core.vehicle.condition.dto;

import com.autonomousapi.core.vehicle.condition.IncidentSeverity;
import com.autonomousapi.core.vehicle.condition.VehicleIncident;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleIncidentResponse(
        UUID id, UUID vehicleId, LocalDate data, IncidentSeverity severidade, String descricao,
        BigDecimal custoReparo) {

    public static VehicleIncidentResponse from(VehicleIncident i) {
        return new VehicleIncidentResponse(
                i.getId(), i.getVehicleId(), i.getData(), i.getSeveridade(), i.getDescricao(), i.getCustoReparo());
    }
}
