package com.autonomousapi.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidTeamInviteTokenException;
import com.autonomousapi.core.error.InvalidTeamRoleException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.team.dto.CreateTeamInviteRequest;
import com.autonomousapi.core.team.dto.TeamOverviewResponse;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class TeamServiceTest {

    private final TeamInviteRepository invites = mock(TeamInviteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final TeamService service = new TeamService(
            invites, users, emailSender, new BCryptPasswordEncoder(), 72, "http://localhost:5173");

    private final UUID tenantId = UUID.randomUUID();
    private final JwtPrincipal gestor = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    @Test
    void conviteRejeitaPapelDeGestaoTotal() {
        var req = new CreateTeamInviteRequest("novo@teste.local", "Novo", Role.GESTOR_FROTA);

        assertThrows(InvalidTeamRoleException.class, () -> service.invite(gestor, req));
    }

    @Test
    void conviteRejeitaEmailJaUsado() {
        when(users.existsByEmail("ja@teste.local")).thenReturn(true);
        var req = new CreateTeamInviteRequest("ja@teste.local", "Alguém", Role.VISUALIZADOR);

        assertThrows(EmailAlreadyUsedException.class, () -> service.invite(gestor, req));
    }

    @Test
    void convitePersisteEDisparaEmailComPapelCorreto() {
        when(invites.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var req = new CreateTeamInviteRequest("novo@teste.local", "Novo Despachante", Role.DESPACHANTE);

        var resp = service.invite(gestor, req);

        assertEquals(Role.DESPACHANTE, resp.role());
        verify(emailSender).sendTeamInviteEmail(eq("novo@teste.local"), eq("Novo Despachante"), any());
    }

    @Test
    void aceiteComTokenExpiradoLancaExcecaoClara() {
        TeamInvite expirado = new TeamInvite(
                tenantId, "novo@teste.local", "Novo", Role.VISUALIZADOR, gestor.userId(),
                "hash-qualquer", Instant.now().minusSeconds(60));
        when(invites.findByTokenHash(any())).thenReturn(Optional.of(expirado));

        assertThrows(InvalidTeamInviteTokenException.class, () -> service.accept("token-raw", "SenhaForte123"));
    }

    @Test
    void aceiteComTokenInexistenteLancaExcecaoClara() {
        when(invites.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidTeamInviteTokenException.class, () -> service.accept("token-raw", "SenhaForte123"));
    }

    @Test
    void aceiteCriaUsuarioNoPapelDoConviteENoTenantDoConvite() {
        TeamInvite convite = new TeamInvite(
                tenantId, "novo@teste.local", "Novo", Role.DESPACHANTE, gestor.userId(),
                "hash-qualquer", Instant.now().plusSeconds(3600));
        when(invites.findByTokenHash(any())).thenReturn(Optional.of(convite));
        when(users.existsByEmail("novo@teste.local")).thenReturn(false);

        service.accept("token-raw", "SenhaForte123");

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertEquals(Role.DESPACHANTE, captor.getValue().getRole());
        assertEquals(tenantId, captor.getValue().getTenantId());
    }

    @Test
    void mudarPapelRejeitaQuandoOAlvoEOProprioGestor() {
        assertThrows(InvalidTeamRoleException.class,
                () -> service.changeRole(gestor, gestor.userId(), Role.VISUALIZADOR));
    }

    @Test
    void mudarPapelRejeitaPromoverParaGestorTotal() {
        UUID alvoId = UUID.randomUUID();

        assertThrows(InvalidTeamRoleException.class, () -> service.changeRole(gestor, alvoId, Role.GESTOR_FROTA));
    }

    @Test
    void mudarPapelRejeitaIntegranteDeOutroTenant() {
        UUID alvoId = UUID.randomUUID();
        User deOutroTenant = new User(UUID.randomUUID(), "outro@teste.local", "hash", Role.VISUALIZADOR);
        when(users.findById(alvoId)).thenReturn(Optional.of(deOutroTenant));

        assertThrows(NotFoundException.class, () -> service.changeRole(gestor, alvoId, Role.DESPACHANTE));
    }

    @Test
    void removerRejeitaQuandoOAlvoEOProprioGestor() {
        assertThrows(InvalidTeamRoleException.class, () -> service.remove(gestor, gestor.userId()));
    }

    @Test
    void removerDesabilitaOUsuarioSemExcluirDeVerdade() {
        UUID alvoId = UUID.randomUUID();
        User membro = new User(tenantId, "membro@teste.local", "hash", Role.VISUALIZADOR);
        when(users.findById(alvoId)).thenReturn(Optional.of(membro));

        service.remove(gestor, alvoId);

        assertFalse(membro.isEnabled());
    }

    @Test
    void overviewSoTrazConvitesAindaUsaveisENaoOsJaUsados() {
        TeamInvite pendente = new TeamInvite(
                tenantId, "pendente@teste.local", "Pendente", Role.VISUALIZADOR, gestor.userId(),
                "hash1", Instant.now().plusSeconds(3600));
        TeamInvite jaUsado = new TeamInvite(
                tenantId, "usado@teste.local", "Usado", Role.DESPACHANTE, gestor.userId(),
                "hash2", Instant.now().plusSeconds(3600));
        jaUsado.markUsed();
        when(invites.findAllByTenantIdAndUsedAtIsNull(tenantId)).thenReturn(List.of(pendente));
        when(users.findAllByTenantIdAndRoleIn(any(), any())).thenReturn(List.of());

        TeamOverviewResponse overview = service.overview(gestor);

        assertEquals(1, overview.convitesPendentes().size());
        assertEquals("pendente@teste.local", overview.convitesPendentes().get(0).email());
    }
}
