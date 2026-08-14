package com.autonomousapi.core.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Assinatura de um tenant (spec 03) — uma por tenant, canal de cobrança modelado desde já. */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, unique = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_source", nullable = false, length = 20)
    private BillingSource billingSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    /** Só controlado pelo core-api, nunca pela Stripe — relógio do trial (ver SubscriptionGate). */
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // JPA
    }

    public Subscription(UUID tenantId, BillingSource billingSource, String stripeCustomerId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.billingSource = billingSource;
        this.status = SubscriptionStatus.INCOMPLETE;
        this.stripeCustomerId = stripeCustomerId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Assinatura "técnica" criada no signup (spec 03 + ADR 0010): TRIALING por
     * {@code trialEndsAt}, sem nenhum contato com a Stripe ainda — só existe pra dar um
     * relógio ao SubscriptionGate antes do primeiro checkout de verdade.
     */
    public static Subscription trial(UUID tenantId, Instant trialEndsAt) {
        Subscription sub = new Subscription(tenantId, BillingSource.WEB_STRIPE, null);
        sub.status = SubscriptionStatus.TRIALING;
        sub.trialEndsAt = trialEndsAt;
        return sub;
    }

    public void applyStripeUpdate(
            String stripeSubscriptionId, SubscriptionStatus status, Instant currentPeriodEnd) {
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.status = status;
        this.currentPeriodEnd = currentPeriodEnd;
        this.updatedAt = Instant.now();
    }

    /** Pode escrever no sistema: pagando de verdade, ou ainda dentro do trial. */
    public boolean permiteEscrita() {
        if (status == SubscriptionStatus.ACTIVE) return true;
        return status == SubscriptionStatus.TRIALING
                && trialEndsAt != null
                && Instant.now().isBefore(trialEndsAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public BillingSource getBillingSource() {
        return billingSource;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }
}
