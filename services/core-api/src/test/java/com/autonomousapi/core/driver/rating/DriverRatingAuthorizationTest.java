package com.autonomousapi.core.driver.rating;

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
 * Spec 07, DoD ("token de motorista não consegue ler driver_rating"). O controller já tem
 * {@code @PreAuthorize} de classe pra GESTOR_FROTA/ADMIN — este teste é o que garante que a
 * trava continua lá numa próxima mudança, em vez de confiar só na leitura do código.
 */
@AutoConfigureMockMvc
@Transactional
class DriverRatingAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void motoristaRecebe403AoLerAvaliacaoDeQualquerMotorista() throws Exception {
        String token = tokenMotorista();

        mockMvc.perform(get("/v1/drivers/" + UUID.randomUUID() + "/ratings/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String tokenMotorista() {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        return jwtService.issueAccessToken(UUID.randomUUID(), "MOTORISTA", tenant.getId());
    }
}
