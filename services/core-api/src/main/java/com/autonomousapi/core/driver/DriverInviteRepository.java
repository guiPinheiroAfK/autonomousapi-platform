package com.autonomousapi.core.driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverInviteRepository extends JpaRepository<DriverInvite, UUID> {

    Optional<DriverInvite> findByTokenHash(String tokenHash);

    List<DriverInvite> findAllByDriverIdAndUsedAtIsNull(UUID driverId);
}
