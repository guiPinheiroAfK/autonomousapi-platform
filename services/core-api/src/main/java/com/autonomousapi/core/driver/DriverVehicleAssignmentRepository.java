package com.autonomousapi.core.driver;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverVehicleAssignmentRepository
        extends JpaRepository<DriverVehicleAssignment, UUID> {

    /** Designação ativa do motorista (ADR 0014) — no máximo uma, garantido por índice parcial. */
    Optional<DriverVehicleAssignment> findByDriverIdAndEndedAtIsNull(UUID driverId);

    /** Designação ativa do veículo — usada para detectar conflito antes de designar. */
    Optional<DriverVehicleAssignment> findByVehicleIdAndEndedAtIsNull(UUID vehicleId);
}
