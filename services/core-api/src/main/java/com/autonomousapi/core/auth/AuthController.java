package com.autonomousapi.core.auth;

import com.autonomousapi.core.auth.dto.AcceptInviteRequest;
import com.autonomousapi.core.auth.dto.ForgotPasswordRequest;
import com.autonomousapi.core.auth.dto.GoogleAuthRequest;
import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.LoginResult;
import com.autonomousapi.core.auth.dto.RefreshRequest;
import com.autonomousapi.core.auth.dto.ResendVerificationRequest;
import com.autonomousapi.core.auth.dto.ResetPasswordRequest;
import com.autonomousapi.core.auth.dto.SelectTenantRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.SignupResponse;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.auth.dto.UserResponse;
import com.autonomousapi.core.auth.dto.VerifyEmailRequest;
import com.autonomousapi.core.driver.DriverInviteService;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.ratelimit.LoginRateLimitGuard;
import com.autonomousapi.core.team.TeamService;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import com.autonomousapi.core.user.permission.UserPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;
    private final LoginRateLimitGuard loginRateLimit;
    private final DriverInviteService driverInviteService;
    private final TeamService teamService;
    private final UserPermissionService permissions;

    public AuthController(
            AuthService authService,
            UserRepository users,
            LoginRateLimitGuard loginRateLimit,
            DriverInviteService driverInviteService,
            TeamService teamService,
            UserPermissionService permissions) {
        this.authService = authService;
        this.users = users;
        this.loginRateLimit = loginRateLimit;
        this.driverInviteService = driverInviteService;
        this.teamService = teamService;
        this.permissions = permissions;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/verify-email")
    public TokenResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return authService.verifyEmail(req.token());
    }

    /** Sempre 202, tenha o e-mail conta ou não — evita descobrir e-mail cadastrado por tentativa. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        authService.resendVerification(req.email());
    }

    /** V34: pode devolver os tokens direto (caso comum) ou uma escolha de tenant, se a
     *  mesma senha bater em mais de uma conta do e-mail — ver {@link LoginResult}. */
    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        loginRateLimit.verificar(req.email(), http);
        return authService.login(req);
    }

    /** Completa um login ambíguo (ver {@link #login}). */
    @PostMapping("/select-tenant")
    public TokenResponse selectTenant(@Valid @RequestBody SelectTenantRequest req) {
        return authService.selectTenant(req);
    }

    /**
     * Login ou cadastro via Google (ADR pendente de registrar): o ID token já é a prova de
     * identidade (o Google assinou e o backend verifica a assinatura), sem senha nem passo
     * de confirmação de e-mail. Sem token nem senha vazando pela rede além do próprio ID
     * token — fluxo padrão de Google Identity Services, tudo client-side até aqui.
     */
    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody GoogleAuthRequest req) {
        return authService.googleAuth(req.idToken());
    }

    /** Sempre 202, e-mail cadastrado ou não — mesmo raciocínio do resend-verification. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
    }

    /**
     * Aceite do convite de motorista (ADR 0013): cria o login MOTORISTA e define a senha.
     * Público — o token do e-mail é a prova de posse. Depois é só entrar pelo app.
     */
    @PostMapping("/accept-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvite(@Valid @RequestBody AcceptInviteRequest req) {
        driverInviteService.accept(req.token(), req.password());
    }

    /** Aceite do convite de equipe (spec 15) — mesmo raciocínio do convite de motorista:
     *  público, o token do e-mail é a prova de posse. */
    @PostMapping("/accept-team-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptTeamInvite(@Valid @RequestBody AcceptInviteRequest req) {
        teamService.accept(req.token(), req.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    /** Usuário autenticado atual (endpoint protegido — exige Bearer token válido). */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = Lookups.orNotFound(users.findById(userId), "Usuário não encontrado.");
        // ADR 0025: recalculado do banco em vez de lido do token — o front usa isso pra
        // esconder botão, e a tela recarregada logo após um ajuste do gestor mostra o estado
        // novo sem esperar o token renovar (a AUTORIZAÇÃO de verdade continua no token).
        return UserResponse.from(user, permissions.effectiveFor(user));
    }
}
