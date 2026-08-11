package com.autonomousapi.core.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Escopo por tenant embutido na própria query — evita vazar veículo de outro tenant. */
    Optional<Vehicle> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndPlateIgnoreCase(UUID tenantId, String plate);

    boolean existsByTenantIdAndPlateIgnoreCaseAndIdNot(UUID tenantId, String plate, UUID id);
}
