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

import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.SignupResponse;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.email.EmailSender;
import com.autonomousapi.core.error.InvalidVerificationTokenException;
import com.autonomousapi.core.security.jwt.JwtService;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
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
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);

    private AuthService service() {
        return new AuthService(
                users, tenants, refreshTokens, verificationTokens, subscriptions, emailSender,
                passwordEncoder, jwtService, 30, 15, 24, "http://localhost:5180");
    }

    @Test
    void signupCriaUsuarioDesabilitadoEEnviaEmailSemDevolverTokens() {
        when(tenants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        SignupResponse response = service().signup(new SignupRequest("nova@frota.com", "senha12345", "Frota Nova"));

        assertEquals("nova@frota.com", response.email());
        verify(subscriptions).save(any());
        verify(emailSender).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("nova@frota.com"), anyString());

        // O usuário salvo precisa estar desabilitado — é a trava real, não só o texto da resposta.
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertFalse(captor.getValue().isEnabled());
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
        when(jwtService.issueAccessToken(any(), anyString(), any())).thenReturn("access-token");

        TokenResponse response = service().verifyEmail(rawToken);

        assertEquals("access-token", response.accessToken());
        assertEquals(true, user.isEnabled());
        verify(refreshTokens, times(1)).save(any());
    }

    @Test
    void resendVerificationNaoFazNadaSeEmailNaoExisteOuJaConfirmado() {
        when(users.findByEmail("inexistente@frota.com")).thenReturn(Optional.empty());
        service().resendVerification("inexistente@frota.com");
        verify(emailSender, never()).sendVerificationEmail(anyString(), anyString());

        User jaConfirmado = new User(UUID.randomUUID(), "ok@frota.com", "hash", Role.GESTOR_FROTA);
        when(users.findByEmail("ok@frota.com")).thenReturn(Optional.of(jaConfirmado));
        service().resendVerification("ok@frota.com");
        verify(emailSender, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationReenviaSeContaAindaNaoConfirmada() {
        User pendente = new User(UUID.randomUUID(), "pendente@frota.com", "hash", Role.GESTOR_FROTA);
        pendente.setEnabled(false);
        when(users.findByEmail("pendente@frota.com")).thenReturn(Optional.of(pendente));
        when(verificationTokens.findAllByUserIdAndUsedAtIsNull(any())).thenReturn(java.util.List.of());

        service().resendVerification("pendente@frota.com");

        verify(emailSender).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("pendente@frota.com"), anyString());
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
