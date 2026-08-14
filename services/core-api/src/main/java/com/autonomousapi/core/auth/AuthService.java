package com.autonomousapi.core.auth;

import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.billing.Subscription;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.InvalidCredentialsException;
import com.autonomousapi.core.error.InvalidRefreshTokenException;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Spec 03 + ADR 0010: 7 dias de uso livre antes de exigir assinatura. */
    private static final long TRIAL_DAYS = 7;

    private final UserRepository users;
    private final TenantRepository tenants;
    private final RefreshTokenRepository refreshTokens;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTtl;
    private final long accessTtlSeconds;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserRepository users,
            TenantRepository tenants,
            RefreshTokenRepository refreshTokens,
            SubscriptionRepository subscriptions,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays,
            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        this.users = users;
        this.tenants = tenants;
        this.refreshTokens = refreshTokens;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.accessTtlSeconds = Duration.ofMinutes(accessTtlMinutes).toSeconds();
    }

    /**
     * Cria um tenant, o primeiro usuário (gestor de frota) e o trial de
     * {@value #TRIAL_DAYS} dias (ver Subscription#trial, SubscriptionGate) — sem isso o
     * tenant recém-criado ficaria bloqueado na primeira escrita.
     */
    @Transactional
    public TokenResponse signup(SignupRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException();
        }
        Tenant tenant = tenants.save(new Tenant(req.tenantName()));
        User user = new User(
                tenant.getId(),
                req.email(),
                passwordEncoder.encode(req.password()),
                Role.GESTOR_FROTA);
        users.save(user);
        subscriptions.save(Subscription.trial(tenant.getId(), Instant.now().plus(Duration.ofDays(TRIAL_DAYS))));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email()).orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /** Valida o refresh token, rotaciona (revoga o usado) e emite um novo par. */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!stored.isActive()) {
            throw new InvalidRefreshTokenException();
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);

        User user = users.findById(stored.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.issueAccessToken(
                user.getId(), user.getRole().name(), user.getTenantId());
        String rawRefresh = generateRawToken();
        refreshTokens.save(new RefreshToken(
                user.getId(), sha256Hex(rawRefresh), Instant.now().plus(refreshTtl)));
        return TokenResponse.bearer(access, rawRefresh, accessTtlSeconds);
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
