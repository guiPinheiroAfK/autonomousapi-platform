package com.autonomousapi.core.billing;

/** Espelha os status de assinatura da Stripe que o webhook processa. */
public enum SubscriptionStatus {
    INCOMPLETE,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED
}
