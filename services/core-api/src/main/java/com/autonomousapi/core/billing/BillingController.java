package com.autonomousapi.core.billing;

import com.autonomousapi.core.billing.dto.BillingPortalSessionResponse;
import com.autonomousapi.core.billing.dto.CheckoutSessionResponse;
import com.autonomousapi.core.billing.dto.SubscriptionResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assinatura do tenant (spec 03, web-first). Leitura/checkout: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/subscription")
    public SubscriptionResponse subscription(Authentication auth) {
        return billingService.getSubscription((JwtPrincipal) auth.getPrincipal());
    }

    @PostMapping("/checkout")
    public CheckoutSessionResponse checkout(Authentication auth) {
        return billingService.createCheckoutSession((JwtPrincipal) auth.getPrincipal());
    }

    @PostMapping("/portal")
    public BillingPortalSessionResponse portal(Authentication auth) {
        return billingService.createPortalSession((JwtPrincipal) auth.getPrincipal());
    }

    /**
     * Webhook da Stripe — SEM autenticação JWT (é a própria Stripe chamando), a segurança vem
     * da verificação de assinatura HMAC dentro do service. Rota liberada em SecurityConfig.
     */
    @PostMapping("/webhook")
    public void webhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        billingService.handleWebhook(payload, signature);
    }
}
