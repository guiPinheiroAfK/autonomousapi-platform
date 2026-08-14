package com.autonomousapi.core.error;

/** Tenant sem trial válido nem assinatura ativa tentando escrever (ver SubscriptionGate). */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
