package com.autonomousapi.core.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Convite/gestão de equipe (spec 15) é Gestor-only — Despachante e Visualizador não
 *  convidam, não gerenciam papel de ninguém, mesmo eles mesmos sendo membros da equipe. */
@AutoConfigureMockMvc
@Transactional
class TeamControllerAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void despachanteRecebe403AoConvidar() throws Exception {
        mockMvc.perform(post("/v1/team/invite")
                        .header("Authorization", "Bearer " + token("DESPACHANTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"novo@teste.local\",\"nome\":\"Novo\",\"role\":\"VISUALIZADOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void visualizadorRecebe403AoListarEquipe() throws Exception {
        mockMvc.perform(get("/v1/team").header("Authorization", "Bearer " + token("VISUALIZADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void despachanteRecebe403AoRemoverIntegrante() throws Exception {
        mockMvc.perform(delete("/v1/team/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token("DESPACHANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorConsegueListarEquipe() throws Exception {
        mockMvc.perform(get("/v1/team").header("Authorization", "Bearer " + token("GESTOR_FROTA")))
                .andExpect(status().isOk());
    }

    private String token(String role) {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        return jwtService.issueAccessToken(UUID.randomUUID(), role, tenant.getId());
    }
}
