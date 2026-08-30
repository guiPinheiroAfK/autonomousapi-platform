package com.autonomousapi.core.billing;

import com.autonomousapi.core.billing.dto.BillingPortalSessionResponse;
import com.autonomousapi.core.billing.dto.CheckoutSessionResponse;
import com.autonomousapi.core.billing.dto.SubscriptionResponse;
import com.autonomousapi.core.error.BillingNotConfiguredException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.VehicleStatus;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing web-first via Stripe Checkout (spec 03): a assinatura é gerenciada no painel web,
 * fora do app mobile (evita ficar preso a IAP da Apple/Google — decisão documentada no spec).
 * Nunca tocamos em dado de cartão: o Checkout é hospedado pela própria Stripe.
 *
 * Sem STRIPE_SECRET_KEY/STRIPE_PRICE_ID configurados (padrão dev/demo), o checkout responde
 * 503 com mensagem clara em vez de tentar chamar a Stripe com credenciais vazias.
 */
@Service
public class BillingService {

    private final SubscriptionRepository subscriptions;
    private final VehicleRepository vehicles;
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;
    private final String stripePriceId;
    private final String webAppUrl;

    public BillingService(
            SubscriptionRepository subscriptions,
            VehicleRepository vehicles,
            @Value("${app.billing.stripe-secret-key}") String stripeSecretKey,
            @Value("${app.billing.stripe-webhook-secret}") String stripeWebhookSecret,
            @Value("${app.billing.stripe-price-id}") String stripePriceId,
            @Value("${app.billing.web-app-url}") String webAppUrl) {
        this.subscriptions = subscriptions;
        this.vehicles = vehicles;
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.stripePriceId = stripePriceId;
        this.webAppUrl = webAppUrl;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(JwtPrincipal principal) {
        return subscriptions.findByTenantId(principal.tenantId())
                .map(s -> new SubscriptionResponse(
                        true, s.getBillingSource().name(), s.getStatus().name(),
                        s.getCurrentPeriodEnd(), s.getTrialEndsAt()))
                .orElseGet(SubscriptionResponse::none);
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(JwtPrincipal principal) {
        if (stripeSecretKey.isBlank() || stripePriceId.isBlank()) {
            throw new BillingNotConfiguredException(
                    "Billing ainda não configurado neste ambiente (STRIPE_SECRET_KEY/STRIPE_PRICE_ID ausentes).");
        }
        Stripe.apiKey = stripeSecretKey;

        long quantity = Math.max(1, vehicles.countByTenantIdAndStatus(principal.tenantId(), VehicleStatus.ATIVO));

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(principal.tenantId().toString())
                .setSuccessUrl(webAppUrl + "/?billing=success")
                .setCancelUrl(webAppUrl + "/?billing=cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(stripePriceId)
                        .setQuantity(quantity)
                        .build())
                .build();

        try {
            Session session = Session.create(params);
            return new CheckoutSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new BillingNotConfiguredException("Falha ao criar sessão de checkout: " + e.getMessage());
        }
    }

    /**
     * Sessão do portal de billing hospedado pela Stripe (cancelar, trocar cartão, baixar
     * nota fiscal) — só existe depois de um checkout real (é o `stripeCustomerId` que a
     * Stripe cria nesse momento, ver {@link #onCheckoutCompleted}); quem ainda está só no
     * trial nunca chega a ver o botão que chama isto (ver BillingPage.tsx).
     */
    @Transactional(readOnly = true)
    public BillingPortalSessionResponse createPortalSession(JwtPrincipal principal) {
        if (stripeSecretKey.isBlank()) {
            throw new BillingNotConfiguredException(
                    "Billing ainda não configurado neste ambiente (STRIPE_SECRET_KEY ausente).");
        }
        String customerId = subscriptions.findByTenantId(principal.tenantId())
                .map(Subscription::getStripeCustomerId)
                .orElse(null);
        if (customerId == null) {
            throw new BillingNotConfiguredException("Nenhuma assinatura Stripe ativa para abrir o portal.");
        }
        Stripe.apiKey = stripeSecretKey;

        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(webAppUrl + "/assinatura")
                        .build();

        try {
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);
            return new BillingPortalSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new BillingNotConfiguredException("Falha ao abrir o portal de billing: " + e.getMessage());
        }
    }

    /** Processa eventos assinados da Stripe — nunca confia em payload sem verificar a assinatura. */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (stripeWebhookSecret.isBlank()) {
            throw new BillingNotConfiguredException("STRIPE_WEBHOOK_SECRET ausente neste ambiente.");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Assinatura do webhook inválida.", e);
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        switch (event.getType()) {
            case "checkout.session.completed" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof Session session) {
                    onCheckoutCompleted(session);
                }
            });
            case "customer.subscription.updated", "customer.subscription.deleted" ->
                    deserializer.getObject().ifPresent(obj -> {
                        if (obj instanceof com.stripe.model.Subscription stripeSub) {
                            onSubscriptionUpdated(stripeSub);
                        }
                    });
            default -> { /* evento não tratado — ignorado de propósito */ }
        }
    }

    private void onCheckoutCompleted(Session session) {
        UUID tenantId = UUID.fromString(session.getClientReferenceId());
        Subscription sub = subscriptions.findByTenantId(tenantId)
                .orElseGet(() -> new Subscription(tenantId, BillingSource.WEB_STRIPE, session.getCustomer()));
        sub.applyStripeUpdate(session.getSubscription(), SubscriptionStatus.ACTIVE, null);
        subscriptions.save(sub);
    }

    private void onSubscriptionUpdated(com.stripe.model.Subscription stripeSub) {
        Optional<Subscription> maybeSub = subscriptions.findByStripeCustomerId(stripeSub.getCustomer());
        if (maybeSub.isEmpty()) return;

        Subscription sub = maybeSub.get();
        SubscriptionStatus status = mapStatus(stripeSub.getStatus());
        // "current_period_end" mudou de nível na API da Stripe (2025): agora vive por item de
        // assinatura, não mais na assinatura em si — pegamos do primeiro item (só um item aqui).
        Long periodEndEpoch = stripeSub.getItems().getData().stream()
                .findFirst()
                .map(com.stripe.model.SubscriptionItem::getCurrentPeriodEnd)
                .orElse(null);
        Instant periodEnd = periodEndEpoch != null ? Instant.ofEpochSecond(periodEndEpoch) : null;
        sub.applyStripeUpdate(stripeSub.getId(), status, periodEnd);
        subscriptions.save(sub);
    }

    private static SubscriptionStatus mapStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.INCOMPLETE;
        };
    }
}
