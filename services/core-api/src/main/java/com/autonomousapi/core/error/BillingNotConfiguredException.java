package com.autonomousapi.core.error;

/** Lançada quando STRIPE_SECRET_KEY/STRIPE_PRICE_ID não estão configurados (dev/demo). */
public class BillingNotConfiguredException extends RuntimeException {
    public BillingNotConfiguredException(String message) {
        super(message);
    }
}
