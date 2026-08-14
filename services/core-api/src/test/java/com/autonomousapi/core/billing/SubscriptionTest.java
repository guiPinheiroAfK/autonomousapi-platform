package com.autonomousapi.core.billing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR 0010: regra de bloqueio de escrita vive só aqui — o SubscriptionGate só delega. */
class SubscriptionTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void ativaSempreLibera() {
        Subscription sub = new Subscription(tenantId, BillingSource.WEB_STRIPE, "cus_1");
        sub.applyStripeUpdate("sub_1", SubscriptionStatus.ACTIVE, null);

        assertTrue(sub.permiteEscrita());
    }

    @Test
    void trialDentroDaJanelaLibera() {
        Subscription sub = Subscription.trial(tenantId, Instant.now().plus(Duration.ofDays(3)));

        assertTrue(sub.permiteEscrita());
    }

    @Test
    void trialVencidoBloqueia() {
        Subscription sub = Subscription.trial(tenantId, Instant.now().minus(Duration.ofSeconds(1)));

        assertFalse(sub.permiteEscrita());
    }

    @Test
    void incompleteBloqueia() {
        Subscription sub = new Subscription(tenantId, BillingSource.WEB_STRIPE, "cus_1");
        // status nasce INCOMPLETE, sem applyStripeUpdate nenhum ainda

        assertFalse(sub.permiteEscrita());
    }

    @Test
    void canceladaBloqueia() {
        Subscription sub = new Subscription(tenantId, BillingSource.WEB_STRIPE, "cus_1");
        sub.applyStripeUpdate("sub_1", SubscriptionStatus.CANCELED, null);

        assertFalse(sub.permiteEscrita());
    }

    @Test
    void trialSemDataDeFimBloqueia() {
        // não deveria acontecer via Subscription.trial(...), mas se acontecer não pode
        // liberar escrita indefinidamente por causa de um trialEndsAt nulo.
        Subscription sub = Subscription.trial(tenantId, null);

        assertFalse(sub.permiteEscrita());
    }
}
