package com.autonomousapi.core.driver;

import com.autonomousapi.core.driver.dto.DriverInviteResponse;
import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.DriverEmailRequiredException;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidDriverInviteTokenException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convite de acesso do motorista (ADR 0013). O gestor dispara o convite; o aceite cria a
 * conta de login (role MOTORISTA) já habilitada e vincula ao registro operacional. Mesmo
 * padrão de token do reset de senha (AuthService): hash SHA-256, nunca o valor cru.
 */
@Service
public class DriverInviteService {

    private final DriverRepository drivers;
    private final DriverInviteRepository invites;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Duration inviteTtl;
    private final String webAppUrl;
    private final SecureRandom random = new SecureRandom();

    public DriverInviteService(
            DriverRepository drivers,
            DriverInviteRepository invites,
            UserRepository users,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.driver-invite-ttl-hours}") long inviteTtlHours,
            @Value("${app.auth.web-app-url}") String webAppUrl) {
        this.drivers = drivers;
        this.invites = invites;
        this.users = users;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.inviteTtl = Duration.ofHours(inviteTtlHours);
        this.webAppUrl = webAppUrl;
    }

    /**
     * Gestor convida um motorista. Exige e-mail cadastrado no motorista (é o destino e será
     * o login). Invalida convites pendentes anteriores antes de emitir o novo.
     */
    @Transactional
    public DriverInviteResponse invite(JwtPrincipal principal, UUID driverId) {
        Driver driver = drivers.findByIdAndTenantId(driverId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
        if (driver.getEmail() == null || driver.getEmail().isBlank()) {
            throw new DriverEmailRequiredException();
        }
        // Se o e-mail já é um login (motorista já convidado/ativo ou colisão com outra conta),
        // não faz sentido reconvidar para um e-mail que já autentica.
        if (users.existsByEmail(driver.getEmail())) {
            throw new EmailAlreadyUsedException();
        }

        invites.findAllByDriverIdAndUsedAtIsNull(driver.getId())
                .forEach(DriverInvite::markUsed);

        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(inviteTtl);
        invites.save(new DriverInvite(driver.getId(), sha256Hex(rawToken), expiresAt));

        String link = webAppUrl + "/aceitar-convite?token=" + rawToken;
        emailSender.sendDriverInviteEmail(driver.getEmail(), link);
        return new DriverInviteResponse(driver.getId(), driver.getEmail(), expiresAt);
    }

    /**
     * Aceite público (o token do e-mail é a prova de posse): cria o app_user MOTORISTA no
     * tenant do motorista, já habilitado, e vincula ao driver. Sem emitir tokens — o
     * motorista entra pelo app com e-mail + senha, igual ao fluxo de reset (ADR 0013).
     */
    @Transactional
    public void accept(String rawToken, String rawPassword) {
        DriverInvite invite = invites.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(InvalidDriverInviteTokenException::new);
        if (!invite.isUsable()) {
            throw new InvalidDriverInviteTokenException();
        }
        Driver driver = drivers.findById(invite.getDriverId())
                .orElseThrow(InvalidDriverInviteTokenException::new);
        if (driver.getEmail() == null || driver.getEmail().isBlank()) {
            throw new InvalidDriverInviteTokenException();
        }
        if (users.existsByEmail(driver.getEmail())) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(
                driver.getTenantId(),
                driver.getEmail(),
                passwordEncoder.encode(rawPassword),
                Role.MOTORISTA);
        users.save(user);

        driver.linkAppUser(user.getId());
        invite.markUsed();
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
