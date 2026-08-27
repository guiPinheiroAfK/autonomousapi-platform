package com.autonomousapi.core.vehicle.condition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleIncidentRepository extends JpaRepository<VehicleIncident, UUID> {

    List<VehicleIncident> findAllByVehicleIdOrderByDataDesc(UUID vehicleId);

    /** Escopa a exclusão ao veículo do path — id de incidente de outro veículo (mesmo que
     *  do mesmo tenant) nunca é encontrado por aqui. */
    Optional<VehicleIncident> findByIdAndVehicleId(UUID id, UUID vehicleId);
}
