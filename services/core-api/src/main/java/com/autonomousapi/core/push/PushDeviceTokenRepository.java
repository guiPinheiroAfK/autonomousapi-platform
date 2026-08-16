package com.autonomousapi.core.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {

    Optional<PushDeviceToken> findByToken(String token);

    List<PushDeviceToken> findAllByUserId(UUID userId);
}
