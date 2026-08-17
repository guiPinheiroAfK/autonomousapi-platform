package com.autonomousapi.core.driver;

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
 * Achado da revisão do plano de rota multi-parada: esconder "Motoristas"/"Relatórios" no nav
 * do web pra MOTORISTA (spec 07, painel enxuto) só protege de verdade se o endpoint por trás
 * também recusa o token — senão é estética. `GET /v1/drivers` e `/v1/drivers/license-expiring`
 * não tinham nenhum {@code @PreAuthorize} antes desta leva; este teste é o que garante que a
 * lacuna não volta silenciosamente numa próxima mudança.
 */
@AutoConfigureMockMvc
@Transactional
class DriverControllerAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void motoristaRecebe403AoListarTodosOsMotoristas() throws Exception {
        String token = tokenMotorista();

        mockMvc.perform(get("/v1/drivers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void motoristaRecebe403AoConsultarCnhsAVencerDaFrota() throws Exception {
        String token = tokenMotorista();

        mockMvc.perform(get("/v1/drivers/license-expiring").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** Achado da revisão de spec (2026-08-17): faltava travar a leitura do detalhe de
     *  outro motorista (nome, CNH, telefone, e-mail) por id direto. */
    @Test
    void motoristaRecebe403AoLerDetalheDeOutroMotoristaPorId() throws Exception {
        String token = tokenMotorista();

        mockMvc.perform(get("/v1/drivers/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String tokenMotorista() {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        return jwtService.issueAccessToken(UUID.randomUUID(), "MOTORISTA", tenant.getId());
    }
}
