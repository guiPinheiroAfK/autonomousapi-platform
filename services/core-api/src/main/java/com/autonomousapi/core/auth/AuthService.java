package com.autonomousapi.core.auth;

import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.LoginResult;
import com.autonomousapi.core.auth.dto.SelectTenantRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.SignupResponse;
import com.autonomousapi.core.auth.dto.TenantChoiceResponse;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.billing.Subscription;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.email.EmailSender;
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
import com.autonomousapi.core.user.permission.Permission;
import com.autonomousapi.core.user.permission.UserPermissionService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import java.util.List;
import java.util.UUID;
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
    private final UserPermissionService permissions;
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
            UserPermissionService permissions,
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
        this.permissions = permissions;
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
        // V34: cadastro sempre cria um tenant novo — nunca colide com a constraint por-tenant
        // de app_user.email, mesmo que esse e-mail já seja membro de equipe em outro lugar
        // (decisão explícita: uma pessoa pode ter uma conta por empresa em que participa).
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

        // V34: e-mail pode ter mais de uma conta (tenants diferentes). Google só prova posse
        // do e-mail, não escolhe entre elas — pega a primeira (mesmo comportamento de antes
        // pro caso comum de uma conta só; ambiguidade real de qual tenant fica pra decisão
        // futura, não vale a pena um segundo fluxo de escolha só pra Google login agora).
        List<User> candidatos = users.findAllByEmail(email);
        User user = candidatos.isEmpty() ? criarUsuarioViaGoogle(email, payload) : candidatos.get(0);
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
        // V34: e-mail pode ter mais de uma conta — reenvia pra todas que ainda estão
        // pendentes de confirmação (cada uma é uma empresa diferente, cada uma pode estar
        // esperando confirmação independente).
        users.findAllByEmail(email).stream()
                .filter(user -> !user.isEnabled())
                .forEach(this::sendVerificationEmail);
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
        // V34: e-mail pode ter mais de uma conta — manda link de redefinição pra cada uma
        // (senha é independente por tenant, então "esqueci a senha" precisa resolver todas).
        users.findAllByEmail(email).forEach(user -> {
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

    /**
     * V34: um e-mail pode ter mais de uma conta (tenants diferentes, senha própria em cada
     * uma). Testa a senha contra TODAS as linhas do e-mail, sem short-circuit no primeiro
     * match — mantém o tempo de resposta estável independente de quantas contas existem, e
     * a mesma {@link InvalidCredentialsException} cobre tanto "e-mail não existe" quanto
     * "senha errada em todas", igual antes (nunca revela quais e-mails têm conta).
     *
     * <p>Se a senha bate em exatamente uma conta, emite os tokens direto (caso comum). Se
     * bate em mais de uma (a pessoa reusou a mesma senha em duas empresas — plausível, já
     * que cada aceite de convite pede senha nova, não há sincronização entre elas), devolve
     * um token curto de "escolha de empresa" em vez de tokens de acesso — ver
     * {@link #selectTenant}.
     */
    @Transactional
    public LoginResult login(LoginRequest req) {
        List<User> candidatos = users.findAllByEmail(req.email()).stream()
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .toList();
        if (candidatos.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        if (candidatos.size() == 1) {
            return LoginResult.tokens(issueTokens(candidatos.get(0)));
        }
        return LoginResult.chooseTenant(issuePendingTenantChoice(req.email(), candidatos));
    }

    private TenantChoiceResponse issuePendingTenantChoice(String email, List<User> candidatos) {
        List<UUID> tenantIds = candidatos.stream().map(User::getTenantId).toList();
        String pendingToken = jwtService.issuePendingLoginToken(email, tenantIds);
        List<TenantChoiceResponse.TenantOption> opcoes = candidatos.stream()
                .map(u -> new TenantChoiceResponse.TenantOption(
                        u.getTenantId(), tenantName(u.getTenantId()), u.getRole().name()))
                .toList();
        return new TenantChoiceResponse(pendingToken, opcoes);
    }

    private String tenantName(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName).orElse(null);
    }

    /** Completa um login ambíguo (ver {@link #login}) — o pending token já prova que a senha
     *  bateu em todas as contas listadas nele; só falta escolher qual tenant usar. */
    @Transactional
    public TokenResponse selectTenant(SelectTenantRequest req) {
        Claims claims;
        try {
            claims = jwtService.parsePendingLoginToken(req.pendingToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException();
        }
        String email = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> tenantIdsClaim = claims.get("tenantIds", List.class);
        boolean tenantPermitido = tenantIdsClaim != null
                && tenantIdsClaim.stream().anyMatch(id -> id.equals(req.tenantId().toString()));
        if (!tenantPermitido) {
            throw new InvalidCredentialsException();
        }
        User user = users.findByEmailAndTenantId(email, req.tenantId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
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
        // ADR 0025: permissão efetiva (padrão do papel ± ajuste por usuário) é resolvida
        // aqui, na emissão — é o único momento em que o sistema vai ao banco por causa de
        // permissão. Como refresh() também passa por aqui, ajuste feito pelo gestor entra
        // em vigor no próximo access token (≤15 min), sem deslogar ninguém.
        List<String> permissoes = permissions.effectiveFor(user).stream()
                .map(Permission::name)
                .toList();
        String access = jwtService.issueAccessToken(
                user.getId(), user.getRole().name(), user.getTenantId(), permissoes);
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
