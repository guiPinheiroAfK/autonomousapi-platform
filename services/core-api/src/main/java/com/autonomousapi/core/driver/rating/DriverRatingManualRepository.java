package com.autonomousapi.core.driver.rating;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRatingManualRepository extends JpaRepository<DriverRatingManual, UUID> {

    List<DriverRatingManual> findAllByDriverIdOrderByCreatedAtDesc(UUID driverId);
}
