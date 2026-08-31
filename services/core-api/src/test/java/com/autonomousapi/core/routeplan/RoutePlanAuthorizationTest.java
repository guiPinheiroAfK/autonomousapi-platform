package com.autonomousapi.core.routeplan;

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

/**
 * Matriz de permissão de rota (spec 15) — garante que a divisão Gestor/Despachante/
 * Visualizador está de verdade no backend, não só escondida na tela. Despachante cria e
 * atribui, mas não cancela; Visualizador só lê.
 */
@AutoConfigureMockMvc
@Transactional
class RoutePlanAuthorizationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;

    @Test
    void despachanteConsegueListarRotas() throws Exception {
        mockMvc.perform(get("/v1/routes/plans").header("Authorization", "Bearer " + token("DESPACHANTE")))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorConsegueListarRotas() throws Exception {
        mockMvc.perform(get("/v1/routes/plans").header("Authorization", "Bearer " + token("VISUALIZADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void visualizadorRecebe403AoCriarRota() throws Exception {
        mockMvc.perform(post("/v1/routes/plans")
                        .header("Authorization", "Bearer " + token("VISUALIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void visualizadorRecebe403AoSugerirOrdem() throws Exception {
        mockMvc.perform(post("/v1/routes/plans/suggest-order")
                        .header("Authorization", "Bearer " + token("VISUALIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stops\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void visualizadorRecebe403AoAtribuirMotorista() throws Exception {
        mockMvc.perform(post("/v1/routes/plans/" + UUID.randomUUID() + "/assign")
                        .header("Authorization", "Bearer " + token("VISUALIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void despachanteRecebe403AoCancelarRota() throws Exception {
        mockMvc.perform(post("/v1/routes/plans/" + UUID.randomUUID() + "/cancel")
                        .header("Authorization", "Bearer " + token("DESPACHANTE")))
                .andExpect(status().isForbidden());
    }

    private String token(String role) {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        return jwtService.issueAccessToken(UUID.randomUUID(), role, tenant.getId());
    }
}
