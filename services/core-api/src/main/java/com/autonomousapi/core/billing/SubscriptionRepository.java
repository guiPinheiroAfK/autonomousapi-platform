package com.autonomousapi.core.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
}
