package com.autonomousapi.core.vehicle.marketvalue;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.marketvalue.dto.VehicleMarketValueRequest;
import com.autonomousapi.core.vehicle.marketvalue.dto.VehicleMarketValueResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Valor de mercado/FIPE (spec 06, item 2). Mesmo escopo por tenant via Vehicle. */
@Service
public class VehicleMarketValueService {

    private final VehicleRepository vehicles;
    private final VehicleMarketValueRepository marketValues;

    public VehicleMarketValueService(VehicleRepository vehicles, VehicleMarketValueRepository marketValues) {
        this.vehicles = vehicles;
        this.marketValues = marketValues;
    }

    /** Cada lançamento é um novo registro (histórico), não um update — permite ver a curva de valor. */
    @Transactional
    public VehicleMarketValueResponse record(JwtPrincipal principal, UUID vehicleId, VehicleMarketValueRequest req) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        VehicleMarketValue value = new VehicleMarketValue(
                vehicle.getId(), req.valorFipe(), req.dataReferencia(), req.codigoFipe());
        marketValues.save(value);
        return VehicleMarketValueResponse.from(value);
    }

    @Transactional(readOnly = true)
    public Optional<VehicleMarketValueResponse> latest(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        return marketValues.findFirstByVehicleIdOrderByDataReferenciaDesc(vehicle.getId())
                .map(VehicleMarketValueResponse::from);
    }

    private Vehicle findOwnedVehicle(JwtPrincipal principal, UUID vehicleId) {
        return vehicles.findByIdAndTenantId(vehicleId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }
}
