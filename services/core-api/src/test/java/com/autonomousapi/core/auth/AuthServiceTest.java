package com.autonomousapi.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.LoginResult;
import com.autonomousapi.core.auth.dto.SelectTenantRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.SignupResponse;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.GoogleAuthNotConfiguredException;
import com.autonomousapi.core.error.InvalidCredentialsException;
import com.autonomousapi.core.error.InvalidPasswordResetTokenException;
import com.autonomousapi.core.error.InvalidVerificationTokenException;
import com.autonomousapi.core.notification.webhook.NotificationWebhookSender;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** ADR 0011: conta nasce desabilitada, só o link de verificação habilita. */
class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
    private final PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final NotificationWebhookSender notificationWebhookSender = mock(NotificationWebhookSender.class);
    private final com.autonomousapi.core.user.permission.UserPermissionService permissions =
            mock(com.autonomousapi.core.user.permission.UserPermissionService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);

    private AuthService service() {
        return new AuthService(
                users, tenants, refreshTokens, verificationTokens, passwordResetTokens, subscriptions, emailSender,
                notificationWebhookSender, permissions, passwordEncoder, jwtService, 30, 15, 24, 60, "http://localhost:5180", "");
    }

    /** GOOGLE_CLIENT_ID vazio (padrão dev/demo) — o botão nem aparece no front, mas o
     *  backend precisa recusar de forma limpa mesmo assim, não tentar verificar contra
     *  um audience vazio. */
    @Test
    void googleAuthSemClientIdConfiguradoLancaErroClaro() {
        assertThrows(GoogleAuthNotConfiguredException.class, () -> service().googleAuth("qualquer-id-token"));
    }

    @Test
    void signupCriaUsuarioDesabilitadoEEnviaEmailSemDevolverTokens() {
        when(tenants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        SignupResponse response = service().signup(new SignupRequest("nova@frota.com", "senha12345", "Frota Nova"));

        assertEquals("nova@frota.com", response.email());
        verify(subscriptions).save(any());
        verify(emailSender).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("nova@frota.com"), anyString());
        // Spec 12: signup dispara notificação operacional interna, sem travar a resposta.
        verify(notificationWebhookSender).notify(org.mockito.ArgumentMatchers.contains("nova@frota.com"));

        // O usuário salvo precisa estar desabilitado — é a trava real, não só o texto da resposta.
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertFalse(captor.getValue().isEnabled());
    }

    /** V34: cadastro sempre cria tenant novo — nunca colide com a constraint por-tenant,
     *  mesmo que o e-mail já seja membro de equipe (ou dono) de outra empresa. */
    @Test
    void signupPermiteEmailJaExistenteEmOutroTenant() {
        when(tenants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        SignupResponse response =
                service().signup(new SignupRequest("ja-tem-conta@frota.com", "senha12345", "Outra Frota"));

        assertEquals("ja-tem-conta@frota.com", response.email());
        verify(users, never()).existsByEmail(anyString());
    }

    @Test
    void loginComUmaContaEmiteTokensDireto() {
        User user = new User(UUID.randomUUID(), "gestor@frota.com", "hash", Role.GESTOR_FROTA);
        when(users.findAllByEmail("gestor@frota.com")).thenReturn(List.of(user));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.issueAccessToken(any(), anyString(), any(), any())).thenReturn("access-token");

        LoginResult result = service().login(new LoginRequest("gestor@frota.com", "senha123"));

        assertEquals("access-token", result.tokens().accessToken());
        assertEquals(null, result.tenantChoice());
    }

    /** V34: a mesma senha bate em duas contas (tenants diferentes) — não escolhe uma
     *  arbitrariamente, devolve as opções. */
    @Test
    void loginComDuasContasMesmaSenhaDevolveEscolhaDeTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        User contaA = new User(tenantA, "duas@frota.com", "hash", Role.GESTOR_FROTA);
        User contaB = new User(tenantB, "duas@frota.com", "hash", Role.DESPACHANTE);
        when(users.findAllByEmail("duas@frota.com")).thenReturn(List.of(contaA, contaB));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.issuePendingLoginToken(org.mockito.ArgumentMatchers.eq("duas@frota.com"), any()))
                .thenReturn("pending-token");
        when(tenants.findById(tenantA)).thenReturn(Optional.of(new Tenant("Empresa A")));
        when(tenants.findById(tenantB)).thenReturn(Optional.of(new Tenant("Empresa B")));

        LoginResult result = service().login(new LoginRequest("duas@frota.com", "senha123"));

        assertEquals(null, result.tokens());
        assertEquals("pending-token", result.tenantChoice().pendingToken());
        assertEquals(2, result.tenantChoice().tenants().size());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginComSenhaErradaEmTodasAsContasLancaInvalidCredentials() {
        User user = new User(UUID.randomUUID(), "gestor@frota.com", "hash", Role.GESTOR_FROTA);
        when(users.findAllByEmail("gestor@frota.com")).thenReturn(List.of(user));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service().login(new LoginRequest("gestor@frota.com", "errada")));
    }

    /** Não pode revelar se o e-mail existe — mesma exceção de senha errada. */
    @Test
    void loginComEmailInexistenteLancaMesmaExcecaoQueSenhaErrada() {
        when(users.findAllByEmail("inexistente@frota.com")).thenReturn(List.of());

        assertThrows(InvalidCredentialsException.class,
                () -> service().login(new LoginRequest("inexistente@frota.com", "qualquer")));
    }

    @Test
    void selectTenantComTokenValidoEmiteTokensDoTenantEscolhido() {
        UUID tenantId = UUID.randomUUID();
        User user = new User(tenantId, "duas@frota.com", "hash", Role.GESTOR_FROTA);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("duas@frota.com");
        when(claims.get("tenantIds", List.class)).thenReturn(List.of(tenantId.toString()));
        when(jwtService.parsePendingLoginToken("pending-token")).thenReturn(claims);
        when(users.findByEmailAndTenantId("duas@frota.com", tenantId)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(any(), anyString(), any(), any())).thenReturn("access-token");

        TokenResponse resp = service().selectTenant(new SelectTenantRequest("pending-token", tenantId));

        assertEquals("access-token", resp.accessToken());
    }

    @Test
    void selectTenantComTenantForaDaListaOriginalRecusa() {
        UUID tenantPermitido = UUID.randomUUID();
        UUID tenantNaoPermitido = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("duas@frota.com");
        when(claims.get("tenantIds", List.class)).thenReturn(List.of(tenantPermitido.toString()));
        when(jwtService.parsePendingLoginToken("pending-token")).thenReturn(claims);

        assertThrows(InvalidCredentialsException.class,
                () -> service().selectTenant(new SelectTenantRequest("pending-token", tenantNaoPermitido)));
    }

    @Test
    void verifyEmailComTokenInvalidoLancaErro() {
        when(verificationTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidVerificationTokenException.class, () -> service().verifyEmail("qualquer-coisa"));
    }

    @Test
    void verifyEmailComTokenValidoHabilitaEEmiteTokens() {
        UUID userId = UUID.randomUUID();
        User user = new User(UUID.randomUUID(), "confirmar@frota.com", "hash", Role.GESTOR_FROTA);
        user.setEnabled(false);

        // Simula o hash real batendo (o service calcula sha256 do raw token recebido).
        String rawToken = "token-de-teste";
        EmailVerificationToken storedToken =
                new EmailVerificationToken(userId, sha256Hex(rawToken), java.time.Instant.now().plusSeconds(3600));

        when(verificationTokens.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(storedToken));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(any(), anyString(), any(), any())).thenReturn("access-token");

        TokenResponse response = service().verifyEmail(rawToken);

        assertEquals("access-token", response.accessToken());
        assertEquals(true, user.isEnabled());
        verify(refreshTokens, times(1)).save(any());
        // Spec 12: confirmação de e-mail também dispara notificação operacional interna.
        verify(notificationWebhookSender).notify(org.mockito.ArgumentMatchers.contains("confirmar@frota.com"));
    }

    @Test
    void resendVerificationNaoFazNadaSeEmailNaoExisteOuJaConfirmado() {
        when(users.findAllByEmail("inexistente@frota.com")).thenReturn(java.util.List.of());
        service().resendVerification("inexistente@frota.com");
        verify(emailSender, never()).sendVerificationEmail(anyString(), anyString());

        User jaConfirmado = new User(UUID.randomUUID(), "ok@frota.com", "hash", Role.GESTOR_FROTA);
        when(users.findAllByEmail("ok@frota.com")).thenReturn(java.util.List.of(jaConfirmado));
        service().resendVerification("ok@frota.com");
        verify(emailSender, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationReenviaSeContaAindaNaoConfirmada() {
        User pendente = new User(UUID.randomUUID(), "pendente@frota.com", "hash", Role.GESTOR_FROTA);
        pendente.setEnabled(false);
        when(users.findAllByEmail("pendente@frota.com")).thenReturn(java.util.List.of(pendente));
        when(verificationTokens.findAllByUserIdAndUsedAtIsNull(any())).thenReturn(java.util.List.of());

        service().resendVerification("pendente@frota.com");

        verify(emailSender).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("pendente@frota.com"), anyString());
    }

    /** V34: e-mail pode ter mais de uma conta — reenvia pra CADA uma ainda não confirmada. */
    @Test
    void resendVerificationReenviaPraTodasAsContasPendentesDoEmail() {
        User pendenteTenantA = new User(UUID.randomUUID(), "duas@frota.com", "hash", Role.GESTOR_FROTA);
        pendenteTenantA.setEnabled(false);
        User pendenteTenantB = new User(UUID.randomUUID(), "duas@frota.com", "hash", Role.GESTOR_FROTA);
        pendenteTenantB.setEnabled(false);
        when(users.findAllByEmail("duas@frota.com")).thenReturn(java.util.List.of(pendenteTenantA, pendenteTenantB));
        when(verificationTokens.findAllByUserIdAndUsedAtIsNull(any())).thenReturn(java.util.List.of());

        service().resendVerification("duas@frota.com");

        verify(emailSender, org.mockito.Mockito.times(2))
                .sendVerificationEmail(org.mockito.ArgumentMatchers.eq("duas@frota.com"), anyString());
    }

    @Test
    void forgotPasswordNaoFazNadaSeEmailNaoExiste() {
        when(users.findAllByEmail("inexistente@frota.com")).thenReturn(java.util.List.of());

        service().forgotPassword("inexistente@frota.com");

        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void forgotPasswordEnviaLinkSeEmailExiste() {
        User user = new User(UUID.randomUUID(), "existe@frota.com", "hash", Role.GESTOR_FROTA);
        when(users.findAllByEmail("existe@frota.com")).thenReturn(java.util.List.of(user));
        when(passwordResetTokens.findAllByUserIdAndUsedAtIsNull(any())).thenReturn(java.util.List.of());

        service().forgotPassword("existe@frota.com");

        verify(emailSender).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("existe@frota.com"), anyString());
    }

    @Test
    void resetPasswordComTokenInvalidoLancaErro() {
        when(passwordResetTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> service().resetPassword("qualquer-coisa", "novaSenha123"));
    }

    @Test
    void resetPasswordTrocaSenhaERevogaRefreshTokens() {
        // User gera o próprio id no construtor — usar user.getId(), não uma UUID solta,
        // pra não descolar do id que o service realmente usa em revokeAllForUser.
        User user = new User(UUID.randomUUID(), "reset@frota.com", "hash-antigo", Role.GESTOR_FROTA);
        String rawToken = "token-de-reset";
        PasswordResetToken storedToken = new PasswordResetToken(
                user.getId(), sha256Hex(rawToken), java.time.Instant.now().plusSeconds(3600));

        when(passwordResetTokens.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(storedToken));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");

        service().resetPassword(rawToken, "novaSenha123");

        assertEquals("hash-novo", user.getPasswordHash());
        verify(refreshTokens).revokeAllForUser(user.getId());
    }

    private static String sha256Hex(String value) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
