package com.autonomousapi.core.affiliate;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateClickRepository extends JpaRepository<AffiliateClick, UUID> {
}
