package com.autonomousapi.core.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assinatura/Billing é assunto de dono de conta (spec 15) — Despachante/Visualizador ficam
 * de fora, inclusive da leitura. Achado da auditoria: {@code GET /v1/billing/subscription}
 * não tinha nenhum {@code @PreAuthorize} antes desta leva; fechado junto.
 */
@AutoConfigureMockMvc
@Transactional
class BillingAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void despachanteRecebe403AoVerAssinatura() throws Exception {
        mockMvc.perform(get("/v1/billing/subscription").header("Authorization", "Bearer " + token("DESPACHANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void visualizadorRecebe403AoVerAssinatura() throws Exception {
        mockMvc.perform(get("/v1/billing/subscription").header("Authorization", "Bearer " + token("VISUALIZADOR")))
                .andExpect(status().isForbidden());
    }

    private String token(String role) {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        return jwtService.issueAccessToken(UUID.randomUUID(), role, tenant.getId());
    }
}
