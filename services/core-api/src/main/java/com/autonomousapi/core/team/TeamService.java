package com.autonomousapi.core.team;

import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidTeamInviteTokenException;
import com.autonomousapi.core.error.InvalidTeamRoleException;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.team.dto.CreateTeamInviteRequest;
import com.autonomousapi.core.team.dto.TeamInviteResponse;
import com.autonomousapi.core.team.dto.TeamMemberResponse;
import com.autonomousapi.core.team.dto.TeamOverviewResponse;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipe e permissões (spec 15): convite de acesso restrito (Despachante/Visualizador) —
 * mesmo padrão de token de {@link com.autonomousapi.core.driver.DriverInviteService}, mas
 * sem vínculo com registro operacional prévio. Só o Gestor convida/gerencia (garantido pelo
 * {@code @PreAuthorize} do controller, não repetido aqui).
 */
@Service
public class TeamService {

    /** Papéis que esse fluxo aceita conceder — nunca GESTOR_FROTA/ADMIN por aqui, ou
     *  convite/troca de papel vira caminho de escalonamento de privilégio. */
    private static final List<Role> PAPEIS_DE_EQUIPE = List.of(Role.DESPACHANTE, Role.VISUALIZADOR);

    /** Papéis que aparecem na visão de equipe (inclui o próprio Gestor, pra ele se ver na lista). */
    private static final List<Role> PAPEIS_DE_GESTAO = List.of(Role.GESTOR_FROTA, Role.DESPACHANTE, Role.VISUALIZADOR);

    private final TeamInviteRepository invites;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Duration inviteTtl;
    private final String webAppUrl;
    private final SecureRandom random = new SecureRandom();

    public TeamService(
            TeamInviteRepository invites,
            UserRepository users,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.team-invite-ttl-hours}") long inviteTtlHours,
            @Value("${app.auth.web-app-url}") String webAppUrl) {
        this.invites = invites;
        this.users = users;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.inviteTtl = Duration.ofHours(inviteTtlHours);
        this.webAppUrl = webAppUrl;
    }

    @Transactional
    public TeamInviteResponse invite(JwtPrincipal gestorPrincipal, CreateTeamInviteRequest req) {
        validarPapelDeEquipe(req.role());
        if (users.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException();
        }
        // Invalida convite pendente anterior pro mesmo e-mail — evita dois convites vivos
        // pra mesma pessoa com token/papel diferentes.
        invites.findAllByEmailAndUsedAtIsNull(req.email()).forEach(TeamInvite::markUsed);

        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(inviteTtl);
        TeamInvite invite = invites.save(new TeamInvite(
                gestorPrincipal.tenantId(), req.email(), req.nome(), req.role(),
                gestorPrincipal.userId(), sha256Hex(rawToken), expiresAt));

        String link = webAppUrl + "/aceitar-convite-equipe?token=" + rawToken;
        emailSender.sendTeamInviteEmail(req.email(), req.nome(), link);
        return TeamInviteResponse.from(invite);
    }

    @Transactional(readOnly = true)
    public TeamOverviewResponse overview(JwtPrincipal gestorPrincipal) {
        List<TeamMemberResponse> membros = users
                .findAllByTenantIdAndRoleIn(gestorPrincipal.tenantId(), PAPEIS_DE_GESTAO).stream()
                .map(TeamMemberResponse::from)
                .toList();
        List<TeamInviteResponse> pendentes = invites
                .findAllByTenantIdAndUsedAtIsNull(gestorPrincipal.tenantId()).stream()
                .filter(TeamInvite::isUsable)
                .map(TeamInviteResponse::from)
                .toList();
        return new TeamOverviewResponse(membros, pendentes);
    }

    @Transactional
    public TeamMemberResponse changeRole(JwtPrincipal gestorPrincipal, UUID userId, Role novoPapel) {
        validarPapelDeEquipe(novoPapel);
        if (userId.equals(gestorPrincipal.userId())) {
            throw new InvalidTeamRoleException("Você não pode mudar o próprio papel por aqui.");
        }
        User alvo = membroDoTenant(gestorPrincipal, userId);
        alvo.mudarPapelDeEquipe(novoPapel);
        return TeamMemberResponse.from(alvo);
    }

    @Transactional
    public void remove(JwtPrincipal gestorPrincipal, UUID userId) {
        if (userId.equals(gestorPrincipal.userId())) {
            throw new InvalidTeamRoleException("Você não pode remover a si mesmo.");
        }
        User alvo = membroDoTenant(gestorPrincipal, userId);
        alvo.setEnabled(false);
    }

    /**
     * Aceite público (o token do e-mail é a prova de posse): cria o app_user no papel
     * definido no convite. Sem emitir tokens — entra pelo login normal, igual ao motorista.
     */
    @Transactional
    public void accept(String rawToken, String rawPassword) {
        TeamInvite invite = invites.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(InvalidTeamInviteTokenException::new);
        if (!invite.isUsable()) {
            throw new InvalidTeamInviteTokenException();
        }
        if (users.existsByEmail(invite.getEmail())) {
            throw new EmailAlreadyUsedException();
        }
        users.save(new User(invite.getTenantId(), invite.getEmail(), passwordEncoder.encode(rawPassword), invite.getRole()));
        invite.markUsed();
    }

    private User membroDoTenant(JwtPrincipal gestorPrincipal, UUID userId) {
        User alvo = Lookups.orNotFound(users.findById(userId), "Integrante não encontrado.");
        if (!gestorPrincipal.tenantId().equals(alvo.getTenantId()) || !PAPEIS_DE_GESTAO.contains(alvo.getRole())) {
            throw new com.autonomousapi.core.error.NotFoundException("Integrante não encontrado.");
        }
        return alvo;
    }

    private void validarPapelDeEquipe(Role role) {
        if (!PAPEIS_DE_EQUIPE.contains(role)) {
            throw new InvalidTeamRoleException("Papel inválido — só DESPACHANTE ou VISUALIZADOR podem ser concedidos por convite/troca de papel.");
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
