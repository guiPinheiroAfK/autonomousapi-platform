package com.autonomousapi.core.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {

    Optional<TeamInvite> findByTokenHash(String tokenHash);

    List<TeamInvite> findAllByTenantIdAndUsedAtIsNull(UUID tenantId);

    List<TeamInvite> findAllByEmailAndUsedAtIsNull(String email);
}
