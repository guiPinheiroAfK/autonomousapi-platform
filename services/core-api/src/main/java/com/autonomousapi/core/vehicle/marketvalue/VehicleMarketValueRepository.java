package com.autonomousapi.core.vehicle.marketvalue;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleMarketValueRepository extends JpaRepository<VehicleMarketValue, UUID> {

    Optional<VehicleMarketValue> findFirstByVehicleIdOrderByDataReferenciaDesc(UUID vehicleId);
}
