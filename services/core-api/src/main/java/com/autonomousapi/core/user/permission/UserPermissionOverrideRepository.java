package com.autonomousapi.core.user.permission;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UUID> {

    List<UserPermissionOverride> findAllByUserId(UUID userId);

    @Modifying
    @Query("delete from UserPermissionOverride o where o.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
