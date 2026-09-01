package com.autonomousapi.core.auth;

import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.SignupResponse;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.billing.Subscription;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.EmailAlreadyUsedException;
import com.autonomousapi.core.error.GoogleAuthNotConfiguredException;
import com.autonomousapi.core.error.InvalidCredentialsException;
import com.autonomousapi.core.error.InvalidPasswordResetTokenException;
import com.autonomousapi.core.error.InvalidRefreshTokenException;
import com.autonomousapi.core.error.InvalidVerificationTokenException;
import com.autonomousapi.core.notification.webhook.NotificationWebhookSender;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
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
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final SubscriptionRepository subscriptions;
    private final EmailSender emailSender;
    private final NotificationWebhookSender notificationWebhookSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTtl;
    private final long accessTtlSeconds;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;
    private final String webAppUrl;
    private final String googleClientId;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserRepository users,
            TenantRepository tenants,
            RefreshTokenRepository refreshTokens,
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository passwordResetTokens,
            SubscriptionRepository subscriptions,
            EmailSender emailSender,
            NotificationWebhookSender notificationWebhookSender,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays,
            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes,
            @Value("${app.auth.email-verification-ttl-hours}") long emailVerificationTtlHours,
            @Value("${app.auth.password-reset-ttl-minutes}") long passwordResetTtlMinutes,
            @Value("${app.auth.web-app-url}") String webAppUrl,
            @Value("${app.auth.google-client-id}") String googleClientId) {
        this.users = users;
        this.tenants = tenants;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.subscriptions = subscriptions;
        this.emailSender = emailSender;
        this.notificationWebhookSender = notificationWebhookSender;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.accessTtlSeconds = Duration.ofMinutes(accessTtlMinutes).toSeconds();
        this.googleClientId = googleClientId;
        // Construir o verifier não faz nenhuma chamada de rede (só monta o cliente HTTP e o
        // cache de chave, ainda vazio) — seguro criar mesmo sem GOOGLE_CLIENT_ID configurado,
        // só não é usado nesse caso (ver googleAuth).
        this.googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        this.emailVerificationTtl = Duration.ofHours(emailVerificationTtlHours);
        this.passwordResetTtl = Duration.ofMinutes(passwordResetTtlMinutes);
        this.webAppUrl = webAppUrl;
    }

    /**
     * Cria um tenant, o primeiro usuário (gestor de frota, DESABILITADO — ADR 0011) e o
     * trial de {@value #TRIAL_DAYS} dias (ver Subscription#trial, SubscriptionGate).
     *
     * O usuário nasce desabilitado e signup NÃO devolve tokens: sem isso, qualquer
     * e-mail (nem precisa ser real) já teria acesso de escrita completo, o que é
     * exatamente o abuso que a confirmação de e-mail existe para impedir. Só o clique no
     * link enviado habilita a conta e emite os tokens de verdade (ver verifyEmail).
     */
    @Transactional
    public SignupResponse signup(SignupRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException();
        }
        Tenant tenant = tenants.save(new Tenant(req.tenantName()));
        User user = new User(
                tenant.getId(),
                req.email(),
                passwordEncoder.encode(req.password()),
                Role.GESTOR_FROTA);
        user.setEnabled(false);
        users.save(user);
        subscriptions.save(Subscription.trial(tenant.getId(), Instant.now().plus(Duration.ofDays(TRIAL_DAYS))));
        sendVerificationEmail(user);
        notificationWebhookSender.notify(
                "novo signup: " + tenant.getName() + " / " + user.getEmail() + ", aguardando confirmacao");
        return SignupResponse.pendingVerification(user.getEmail());
    }

    /**
     * Login OU cadastro via Google, na mesma chamada — o ID token já prova posse do e-mail
     * (Google verificou), então não existe o passo de "confirme seu e-mail" que o signup por
     * senha tem: usuário novo já nasce habilitado. E-mail existente vira login direto, mesmo
     * que a conta tenha sido criada originalmente por senha — é a mesma pessoa provando posse
     * do mesmo e-mail por outro caminho, não duas contas.
     *
     * `password_hash` continua NOT NULL no schema (não vale a pena migrar isso só por causa
     * do Google) — conta só-Google recebe um hash de valor aleatório, nunca comunicado a
     * ninguém, então login por senha nessa conta simplesmente nunca bate.
     *
     * Nome do tenant: sem um passo de onboarding pra perguntar "nome da frota" (o Google só
     * devolve o nome da PESSOA), usa o nome da pessoa como placeholder — dá pra editar depois
     * se o produto ganhar uma tela de configurações de tenant.
     */
    @Transactional
    public TokenResponse googleAuth(String idToken) {
        if (googleClientId.isBlank()) {
            throw new GoogleAuthNotConfiguredException(
                    "Login com Google ainda não configurado neste ambiente (GOOGLE_CLIENT_ID ausente).");
        }

        GoogleIdToken.Payload payload = verifyGoogleIdToken(idToken);
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidCredentialsException();
        }

        User user = users.findByEmail(email).orElseGet(() -> criarUsuarioViaGoogle(email, payload));
        if (!user.isEnabled()) {
            // Conta existia via signup por senha, esperando confirmação por e-mail — o
            // login com Google já prova posse do mesmo jeito, não faz sentido travar aqui.
            user.setEnabled(true);
            users.save(user);
        }
        return issueTokens(user);
    }

    private GoogleIdToken.Payload verifyGoogleIdToken(String idToken) {
        try {
            GoogleIdToken token = googleIdTokenVerifier.verify(idToken);
            if (token == null) {
                throw new InvalidCredentialsException();
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidCredentialsException();
        }
    }

    private User criarUsuarioViaGoogle(String email, GoogleIdToken.Payload payload) {
        Object nome = payload.get("name");
        Tenant tenant = tenants.save(new Tenant(nome instanceof String s && !s.isBlank() ? s : email));
        User novo = new User(
                tenant.getId(), email, passwordEncoder.encode(generateRawToken()), Role.GESTOR_FROTA);
        users.save(novo);
        subscriptions.save(Subscription.trial(tenant.getId(), Instant.now().plus(Duration.ofDays(TRIAL_DAYS))));
        return novo;
    }

    /**
     * Habilita a conta e já emite tokens (evita o usuário ter que logar de novo depois
     * de confirmar — o clique no e-mail já é a prova de posse da conta).
     */
    @Transactional
    public TokenResponse verifyEmail(String rawToken) {
        String hash = sha256Hex(rawToken);
        EmailVerificationToken token = verificationTokens.findByTokenHash(hash)
                .orElseThrow(InvalidVerificationTokenException::new);
        if (!token.isUsable()) {
            throw new InvalidVerificationTokenException();
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(InvalidVerificationTokenException::new);

        token.markUsed();
        verificationTokens.save(token);
        user.setEnabled(true);
        users.save(user);

        String tenantName = tenants.findById(user.getTenantId())
                .map(Tenant::getName)
                .orElse(user.getTenantId().toString());
        notificationWebhookSender.notify("conta confirmada: " + tenantName + " / " + user.getEmail());

        return issueTokens(user);
    }

    /**
     * Silencioso de propósito quanto ao motivo de não reenviar (e-mail não existe, ou já
     * está confirmado): responder diferente nos dois casos permitiria descobrir se um
     * e-mail está cadastrado no sistema só tentando reenviar confirmação pra ele.
     */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(email)
                .filter(user -> !user.isEnabled())
                .ifPresent(this::sendVerificationEmail);
    }

    private void sendVerificationEmail(User user) {
        verificationTokens.findAllByUserIdAndUsedAtIsNull(user.getId())
                .forEach(EmailVerificationToken::markUsed);

        String rawToken = generateRawToken();
        verificationTokens.save(new EmailVerificationToken(
                user.getId(), sha256Hex(rawToken), Instant.now().plus(emailVerificationTtl)));

        String link = webAppUrl + "/verificar-email?token=" + rawToken;
        emailSender.sendVerificationEmail(user.getEmail(), link);
    }

    /**
     * Silencioso quanto a e-mail não existir, mesmo raciocínio do resendVerification —
     * responder diferente vazaria quais e-mails estão cadastrados.
     */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(email).ifPresent(user -> {
            passwordResetTokens.findAllByUserIdAndUsedAtIsNull(user.getId())
                    .forEach(PasswordResetToken::markUsed);

            String rawToken = generateRawToken();
            passwordResetTokens.save(new PasswordResetToken(
                    user.getId(), sha256Hex(rawToken), Instant.now().plus(passwordResetTtl)));

            String link = webAppUrl + "/redefinir-senha?token=" + rawToken;
            emailSender.sendPasswordResetEmail(user.getEmail(), link);
        });
    }

    /**
     * Troca a senha e revoga TODOS os refresh tokens do usuário — se um token tivesse
     * vazado, trocar a senha precisa derrubar quem estava usando ele, não só bloquear
     * login novo. Não devolve tokens novos: login de novo com a senha nova é a
     * confirmação de que a pessoa realmente sabe/lembra a senha que acabou de escolher.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = sha256Hex(rawToken);
        PasswordResetToken token = passwordResetTokens.findByTokenHash(hash)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (!token.isUsable()) {
            throw new InvalidPasswordResetTokenException();
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        token.markUsed();
        passwordResetTokens.save(token);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        refreshTokens.revokeAllForUser(user.getId());
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
