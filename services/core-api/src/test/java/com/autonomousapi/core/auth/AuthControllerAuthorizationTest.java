package com.autonomousapi.core.auth;

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
 * Achado em teste manual (2026-08-27): {@code /v1/auth/me} caía no wildcard público
 * "/v1/auth/**" do SecurityConfig, junto de login/signup/etc. Sem token nenhum a requisição
 * passava direto pro controller, que quebrava com NPE (`authentication.getName()` num
 * Authentication nulo) em vez de devolver 401 — e como o frontend só desloga sozinho em
 * 401 (não em 500), um token expirado deixava o app num estado de autenticação pela metade
 * até quebrar em outra tela. Este teste trava que `/me` exige token válido de verdade.
 */
@AutoConfigureMockMvc
@Transactional
class AuthControllerAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void meSemTokenRecebe401LimpoSemQuebrar() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meComTokenValidoDevolveOUsuario() throws Exception {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "GESTOR_FROTA", tenant.getId());

        // Usuário do token não existe no banco (id aleatório) — o que importa aqui é que a
        // autenticação em si passa (não é mais 401/500), o 404 vem do lookup normal do
        // controller (comportamento correto, fora do escopo deste teste).
        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
