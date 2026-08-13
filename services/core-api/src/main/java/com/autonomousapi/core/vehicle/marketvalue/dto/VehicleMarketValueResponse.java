package com.autonomousapi.core.vehicle.marketvalue.dto;

import com.autonomousapi.core.vehicle.marketvalue.VehicleMarketValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleMarketValueResponse(
        UUID vehicleId, BigDecimal valorFipe, LocalDate dataReferencia, String codigoFipe) {

    public static VehicleMarketValueResponse from(VehicleMarketValue v) {
        return new VehicleMarketValueResponse(
                v.getVehicleId(), v.getValorFipe(), v.getDataReferencia(), v.getCodigoFipe());
    }
}
