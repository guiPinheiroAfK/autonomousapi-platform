package com.autonomousapi.core.billing.dto;

import java.time.Instant;

public record SubscriptionResponse(
        boolean hasSubscription, String billingSource, String status, Instant currentPeriodEnd) {

    public static SubscriptionResponse none() {
        return new SubscriptionResponse(false, null, null, null);
    }
}
