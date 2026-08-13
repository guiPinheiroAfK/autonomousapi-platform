package com.autonomousapi.core.vehicle.condition;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleConditionScoreRepository extends JpaRepository<VehicleConditionScore, UUID> {

    Optional<VehicleConditionScore> findByVehicleId(UUID vehicleId);
}
