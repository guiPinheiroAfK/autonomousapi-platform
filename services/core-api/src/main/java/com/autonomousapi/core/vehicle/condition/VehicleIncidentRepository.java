package com.autonomousapi.core.vehicle.condition;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleIncidentRepository extends JpaRepository<VehicleIncident, UUID> {

    List<VehicleIncident> findAllByVehicleIdOrderByDataDesc(UUID vehicleId);
}
