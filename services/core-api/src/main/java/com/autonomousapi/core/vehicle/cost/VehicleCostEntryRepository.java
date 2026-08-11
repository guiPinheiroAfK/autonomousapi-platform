package com.autonomousapi.core.vehicle.cost;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleCostEntryRepository extends JpaRepository<VehicleCostEntry, UUID> {

    List<VehicleCostEntry> findAllByVehicleIdOrderByOccurredAtDesc(UUID vehicleId);

    Optional<VehicleCostEntry> findByIdAndVehicleId(UUID id, UUID vehicleId);

    @Query("select coalesce(sum(c.amount), 0) from VehicleCostEntry c where c.vehicleId = :vehicleId")
    BigDecimal sumAmountByVehicleId(@Param("vehicleId") UUID vehicleId);
}
