package com.autonomousapi.core.passenger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<Passenger, UUID> {

    List<Passenger> findAllByTenantIdOrderByNomeAsc(UUID tenantId);

    Optional<Passenger> findByIdAndTenantId(UUID id, UUID tenantId);
}
