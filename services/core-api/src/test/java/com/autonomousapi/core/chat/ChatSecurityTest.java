package com.autonomousapi.core.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autonomousapi.core.IntegrationTestBase;
import com.autonomousapi.core.billing.Subscription;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras não-negociáveis do spec 07 (DoD, linha "token de motorista não consegue... enviar
 * mensagem de atribuição de rota, nem iniciar conversa fora do par autorizado") — testadas
 * via HTTP real, não só lidas no código.
 */
@AutoConfigureMockMvc
@Transactional
class ChatSecurityTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TenantRepository tenants;
    @Autowired DriverRepository drivers;
    @Autowired ChatConversationRepository conversations;
    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;

    /** POST/PUT/PATCH/DELETE passam pelo SubscriptionGate (ADR 0010) antes de chegar no
     *  @PreAuthorize — sem trial/assinatura ativa, o tenant de teste tomaria 402 antes de
     *  qualquer verificação de papel, mascarando o que este teste quer provar. */
    private Tenant tenantComEscritaLiberada() {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        subscriptions.save(Subscription.trial(tenant.getId(), Instant.now().plus(30, ChronoUnit.DAYS)));
        return tenant;
    }

    @Test
    void motoristaRecebe403AoTentarIniciarConversa() throws Exception {
        Tenant tenant = tenantComEscritaLiberada();
        String token = jwtService.issueAccessToken(UUID.randomUUID(), "MOTORISTA", tenant.getId());

        mockMvc.perform(post("/v1/chat/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void motoristaRecebe403AoTentarEnviarMensagemDeAtribuicaoDeRota() throws Exception {
        Tenant tenant = tenantComEscritaLiberada();
        User gestor = users.save(new User(tenant.getId(), "gestor@teste.com", "hash", Role.GESTOR_FROTA));
        Driver driver = drivers.save(new Driver(tenant.getId(), "Eduardo", "12345678901", null));
        User motoristaUser = users.save(new User(tenant.getId(), "eduardo@teste.com", "hash", Role.MOTORISTA));
        driver.linkAppUser(motoristaUser.getId());
        String token = jwtService.issueAccessToken(motoristaUser.getId(), "MOTORISTA", tenant.getId());

        ChatConversation conv = conversations.save(new ChatConversation(tenant.getId(), gestor.getId(), driver.getId(), null));

        mockMvc.perform(post("/v1/chat/conversations/" + conv.getId() + "/route-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routePlanId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    /** Motorista B não é participante da conversa entre o gestor e o motorista A — a
     *  resposta é 404, não 403, pra não revelar que a conversa existe (mesma decisão já
     *  documentada em ChatService.findAsParticipant). Leitura não passa pelo
     *  SubscriptionGate (só verbo mutante), então não precisa de assinatura ativa aqui. */
    @Test
    void motoristaForaDoParRecebe404AoTentarLerMensagensDaConversa() throws Exception {
        Tenant tenant = tenants.save(new Tenant("Frota de Teste"));
        User gestor = users.save(new User(tenant.getId(), "gestor2@teste.com", "hash", Role.GESTOR_FROTA));
        Driver driverA = drivers.save(new Driver(tenant.getId(), "Eduardo", "12345678901", null));
        Driver driverB = drivers.save(new Driver(tenant.getId(), "Carlos", "98765432100", null));
        User motoristaBUser = users.save(new User(tenant.getId(), "carlos@teste.com", "hash", Role.MOTORISTA));
        driverB.linkAppUser(motoristaBUser.getId());
        String tokenB = jwtService.issueAccessToken(motoristaBUser.getId(), "MOTORISTA", tenant.getId());

        ChatConversation conv = conversations.save(new ChatConversation(tenant.getId(), gestor.getId(), driverA.getId(), null));

        mockMvc.perform(get("/v1/chat/conversations/" + conv.getId() + "/messages")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
