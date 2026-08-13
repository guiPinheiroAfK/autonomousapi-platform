package com.autonomousapi.core.driver.rating;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRatingSummaryRepository extends JpaRepository<DriverRatingSummary, UUID> {

    Optional<DriverRatingSummary> findByDriverId(UUID driverId);
}
