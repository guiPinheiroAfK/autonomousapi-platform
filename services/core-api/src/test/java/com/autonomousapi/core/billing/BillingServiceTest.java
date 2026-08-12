package com.autonomousapi.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.billing.dto.SubscriptionResponse;
import com.autonomousapi.core.error.BillingNotConfiguredException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingServiceTest {

    private final SubscriptionRepository subscriptionRepo = mock(SubscriptionRepository.class);
    private final VehicleRepository vehicleRepo = mock(VehicleRepository.class);

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    private BillingService serviceWithoutStripeKey() {
        return new BillingService(subscriptionRepo, vehicleRepo, "", "", "", "http://localhost:5180");
    }

    @Test
    void semAssinaturaRetornaHasSubscriptionFalso() {
        when(subscriptionRepo.findByTenantId(tenantId)).thenReturn(Optional.empty());

        SubscriptionResponse response = serviceWithoutStripeKey().getSubscription(principal);

        assertFalse(response.hasSubscription());
    }

    @Test
    void comAssinaturaRetornaStatusEOrigem() {
        Subscription sub = new Subscription(tenantId, BillingSource.WEB_STRIPE, "cus_123");
        when(subscriptionRepo.findByTenantId(tenantId)).thenReturn(Optional.of(sub));

        SubscriptionResponse response = serviceWithoutStripeKey().getSubscription(principal);

        assertTrue(response.hasSubscription());
        assertEquals("WEB_STRIPE", response.billingSource());
        assertEquals("INCOMPLETE", response.status());
    }

    @Test
    void checkoutSemChaveStripeConfiguradaLancaErroClaro() {
        BillingService service = serviceWithoutStripeKey();

        assertThrows(BillingNotConfiguredException.class, () -> service.createCheckoutSession(principal));
    }

    @Test
    void webhookSemSegredoConfiguradoLancaErroClaro() {
        BillingService service = serviceWithoutStripeKey();

        assertThrows(BillingNotConfiguredException.class, () -> service.handleWebhook("{}", "assinatura-fake"));
    }
}
