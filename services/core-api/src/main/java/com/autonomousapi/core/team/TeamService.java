package com.autonomousapi.core.team;

import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidTeamInviteTokenException;
import com.autonomousapi.core.error.InvalidTeamRoleException;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.error.NotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserHardDeleteAttempt hardDeleteAttempt;
    private final Duration inviteTtl;
    private final String webAppUrl;
    private final SecureRandom random = new SecureRandom();

    public TeamService(
            TeamInviteRepository invites,
            UserRepository users,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            UserHardDeleteAttempt hardDeleteAttempt,
            @Value("${app.auth.team-invite-ttl-hours}") long inviteTtlHours,
            @Value("${app.auth.web-app-url}") String webAppUrl) {
        this.invites = invites;
        this.users = users;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.hardDeleteAttempt = hardDeleteAttempt;
        this.inviteTtl = Duration.ofHours(inviteTtlHours);
        this.webAppUrl = webAppUrl;
    }

    @Transactional
    public TeamInviteResponse invite(JwtPrincipal gestorPrincipal, CreateTeamInviteRequest req) {
        validarPapelDeEquipe(req.role());
        // V34: e-mail é único por tenant, não mais globalmente — bloqueia só se já for
        // membro ATIVO desta empresa (uma pessoa pode ter conta em várias empresas, e um
        // membro removido antes pode ser convidado de novo — accept() reativa a linha).
        if (users.findByEmailAndTenantId(req.email(), gestorPrincipal.tenantId())
                .filter(User::isEnabled)
                .isPresent()) {
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
        // Nulo (clientes antigos) mantém o comportamento de sempre enviar.
        if (!Boolean.FALSE.equals(req.enviarPorEmail())) {
            emailSender.sendTeamInviteEmail(req.email(), req.nome(), link);
        }
        // O link também volta na resposta (só aqui, na criação — nunca reconstruível depois,
        // só o hash fica salvo): sem domínio de e-mail verificado ainda, o gestor copia e
        // manda por fora (WhatsApp etc.) em vez de depender da entrega chegar.
        return TeamInviteResponse.from(invite, link);
    }

    /** Cancela um convite ainda pendente — mesmo efeito de reconvidar o mesmo e-mail
     *  (invalida via {@code markUsed}), só que sem precisar preencher o form de novo. */
    @Transactional
    public void cancelInvite(JwtPrincipal gestorPrincipal, UUID inviteId) {
        TeamInvite invite = Lookups.orNotFound(invites.findById(inviteId), "Convite não encontrado.");
        if (!invite.getTenantId().equals(gestorPrincipal.tenantId())) {
            throw new NotFoundException("Convite não encontrado.");
        }
        invite.markUsed();
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

    /**
     * V34: tenta apagar de verdade primeiro (libera o e-mail pra reuso — em outro tenant, ou
     * reconvidando pra este mesmo depois) — só cai pro desativar de antes se o alvo já tiver
     * histórico próprio (rota criada, mensagem enviada, refresh token de uma sessão anterior,
     * etc. — qualquer FK sem {@code ON DELETE} recusa o DELETE). A tentativa roda numa
     * transação própria ({@link UserHardDeleteAttempt}) — tentar dentro desta mesma transação
     * marcaria ela inteira como rollback-only assim que o Postgres recusasse (achado ao vivo:
     * a resposta virava 401 genérico em vez do 204 esperado, mesmo capturando a exceção).
     */
    @Transactional
    public void remove(JwtPrincipal gestorPrincipal, UUID userId) {
        if (userId.equals(gestorPrincipal.userId())) {
            throw new InvalidTeamRoleException("Você não pode remover a si mesmo.");
        }
        User alvo = membroDoTenant(gestorPrincipal, userId);
        try {
            hardDeleteAttempt.tryDelete(alvo.getId());
        } catch (DataIntegrityViolationException e) {
            alvo.setEnabled(false);
        }
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
        // V34: se já existe uma linha (e-mail, tenant) desabilitada — foi removido antes e
        // está sendo convidado de novo pra mesma empresa — reativa em vez de inserir outra
        // (evita colidir com a constraint por-tenant e preserva o histórico da conta antiga).
        User alvo = users.findByEmailAndTenantId(invite.getEmail(), invite.getTenantId())
                .orElse(null);
        if (alvo != null) {
            if (alvo.isEnabled()) {
                throw new EmailAlreadyUsedException();
            }
            alvo.setEnabled(true);
            alvo.mudarPapelDeEquipe(invite.getRole());
            alvo.setPasswordHash(passwordEncoder.encode(rawPassword));
            users.save(alvo);
        } else {
            users.save(new User(invite.getTenantId(), invite.getEmail(), passwordEncoder.encode(rawPassword), invite.getRole()));
        }
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
