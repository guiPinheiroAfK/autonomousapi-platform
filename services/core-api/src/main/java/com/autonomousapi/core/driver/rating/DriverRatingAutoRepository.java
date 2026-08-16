package com.autonomousapi.core.driver.rating;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRatingAutoRepository extends JpaRepository<DriverRatingAuto, UUID> {

    List<DriverRatingAuto> findAllByDriverId(UUID driverId);
}
