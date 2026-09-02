package com.autonomousapi.core.user.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.user.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR 0025 — padrão do papel ± ajuste por usuário. */
class UserPermissionServiceTest {

    private final UserPermissionOverrideRepository overrides = mock(UserPermissionOverrideRepository.class);
    private final UserPermissionService service = new UserPermissionService(overrides);

    private final UUID userId = UUID.randomUUID();

    /** A garantia mais importante da ADR 0025: quem não mexeu em permissão nenhuma continua
     *  exatamente com o acesso que a spec 15 dava. */
    @Test
    void padraoDoDespachanteReproduzOComportamentoDaSpec15() {
        when(overrides.findAllByUserId(userId)).thenReturn(List.of());

        Set<Permission> efetivas = service.effectiveFor(userId, Role.DESPACHANTE);

        assertTrue(efetivas.contains(Permission.FROTA_VER), "Despachante lê tudo");
        assertTrue(efetivas.contains(Permission.CUSTOS_VER));
        assertTrue(efetivas.contains(Permission.ROTAS_ESCREVER), "e escreve em rota");
        assertFalse(efetivas.contains(Permission.FROTA_ESCREVER), "mas não escreve fora de rota");
        assertFalse(efetivas.contains(Permission.CUSTOS_ESCREVER));
    }

    @Test
    void padraoDoVisualizadorSoLeExcetoMensagens() {
        when(overrides.findAllByUserId(userId)).thenReturn(List.of());

        Set<Permission> efetivas = service.effectiveFor(userId, Role.VISUALIZADOR);

        assertTrue(efetivas.contains(Permission.ROTAS_VER));
        assertFalse(efetivas.contains(Permission.ROTAS_ESCREVER));
        // Sem isso o chat de equipe (ADR 0023), que já era aberto a qualquer membro,
        // deixaria de funcionar pro Visualizador.
        assertTrue(efetivas.contains(Permission.MENSAGENS_ESCREVER));
    }

    @Test
    void overrideConcedeAlgoForaDoPadraoDoPapel() {
        when(overrides.findAllByUserId(userId)).thenReturn(
                List.of(new UserPermissionOverride(userId, Permission.FROTA_ESCREVER, true)));

        Set<Permission> efetivas = service.effectiveFor(userId, Role.VISUALIZADOR);

        assertTrue(efetivas.contains(Permission.FROTA_ESCREVER));
    }

    @Test
    void overrideRevogaAlgoQueOPapelDavaPorPadrao() {
        when(overrides.findAllByUserId(userId)).thenReturn(
                List.of(new UserPermissionOverride(userId, Permission.CUSTOS_VER, false)));

        Set<Permission> efetivas = service.effectiveFor(userId, Role.DESPACHANTE);

        assertFalse(efetivas.contains(Permission.CUSTOS_VER));
        assertTrue(efetivas.contains(Permission.FROTA_VER), "revogar um módulo não mexe nos outros");
    }

    /** Dono da conta não é ajustável — um override antigo (de quando a pessoa era
     *  Despachante e foi promovida) não pode tirar acesso de quem paga a conta. */
    @Test
    void gestorIgnoraOverrideEFicaComTudo() {
        when(overrides.findAllByUserId(userId)).thenReturn(
                List.of(new UserPermissionOverride(userId, Permission.CUSTOS_VER, false)));

        Set<Permission> efetivas = service.effectiveFor(userId, Role.GESTOR_FROTA);

        assertEquals(EnumSet.allOf(Permission.class), efetivas);
    }

    /** O app do motorista consome chat, lista de veículos e pontos de recarga pelos mesmos
     *  endpoints do painel — fechar esses GETs sem isso derrubaria o app. */
    @Test
    void motoristaMantemOQueOAppDeleUsa() {
        Set<Permission> efetivas = service.effectiveFor(userId, Role.MOTORISTA);

        assertTrue(efetivas.contains(Permission.MENSAGENS_VER));
        assertTrue(efetivas.contains(Permission.MENSAGENS_ESCREVER));
        assertTrue(efetivas.contains(Permission.FROTA_VER));
        assertTrue(efetivas.contains(Permission.RECARGA_VER));
        assertFalse(efetivas.contains(Permission.FROTA_ESCREVER));
        assertFalse(efetivas.contains(Permission.CUSTOS_VER), "motorista nunca vê custo da frota");
    }

    @Test
    void replaceOverridesGravaSoADiferencaEmRelacaoAoPadrao() {
        Set<Permission> desejadas = EnumSet.copyOf(RolePermissionDefaults.forRole(Role.VISUALIZADOR));
        desejadas.add(Permission.FROTA_ESCREVER); // concede algo fora do padrão
        desejadas.remove(Permission.CUSTOS_VER); // tira algo que o padrão dava

        service.replaceOverrides(userId, Role.VISUALIZADOR, desejadas);

        verify(overrides).deleteAllByUserId(userId);
        ArgumentCaptor<List<UserPermissionOverride>> captor = ArgumentCaptor.forClass(List.class);
        verify(overrides).saveAll(captor.capture());

        List<UserPermissionOverride> gravados = captor.getValue();
        assertEquals(2, gravados.size(), "só as duas diferenças viram linha");
        assertTrue(gravados.stream().anyMatch(o -> o.getPermission() == Permission.FROTA_ESCREVER && o.isAllowed()));
        assertTrue(gravados.stream().anyMatch(o -> o.getPermission() == Permission.CUSTOS_VER && !o.isAllowed()));
    }

    /** Igual ao padrão do papel = nenhum override (o caso mais comum: gestor abre a tela,
     *  não muda nada e salva). */
    @Test
    void replaceOverridesComOPadraoNaoGravaNada() {
        service.replaceOverrides(userId, Role.DESPACHANTE, RolePermissionDefaults.forRole(Role.DESPACHANTE));

        ArgumentCaptor<List<UserPermissionOverride>> captor = ArgumentCaptor.forClass(List.class);
        verify(overrides).saveAll(captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    /** "Tirar tudo" é escolha legítima — não pode explodir em EnumSet.copyOf(vazio). */
    @Test
    void replaceOverridesAceitaConjuntoVazio() {
        Set<Permission> efetivas = service.replaceOverrides(userId, Role.VISUALIZADOR, Set.of());

        assertTrue(efetivas.isEmpty());
        verify(overrides).saveAll(any());
    }
}
