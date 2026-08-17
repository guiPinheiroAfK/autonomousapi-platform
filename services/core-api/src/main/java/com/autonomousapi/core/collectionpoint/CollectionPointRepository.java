package com.autonomousapi.core.collectionpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionPointRepository extends JpaRepository<CollectionPoint, UUID> {

    List<CollectionPoint> findAllByTenantIdAndAtivoTrueOrderByNomeAsc(UUID tenantId);

    List<CollectionPoint> findAllByTenantIdOrderByNomeAsc(UUID tenantId);

    Optional<CollectionPoint> findByIdAndTenantId(UUID id, UUID tenantId);
}
