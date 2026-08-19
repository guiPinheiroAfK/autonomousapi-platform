package com.autonomousapi.core.pricing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuelPriceReferenceRepository extends JpaRepository<FuelPriceReference, UUID> {

    Optional<FuelPriceReference> findByTenantIdAndTipoCombustivel(UUID tenantId, String tipoCombustivel);
}
