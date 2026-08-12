package com.autonomousapi.core.auth;

import com.autonomousapi.core.auth.dto.LoginRequest;
import com.autonomousapi.core.auth.dto.RefreshRequest;
import com.autonomousapi.core.auth.dto.SignupRequest;
import com.autonomousapi.core.auth.dto.TokenResponse;
import com.autonomousapi.core.auth.dto.UserResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.ratelimit.LoginRateLimitGuard;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
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

    public AuthController(
            AuthService authService, UserRepository users, LoginRateLimitGuard loginRateLimit) {
        this.authService = authService;
        this.users = users;
        this.loginRateLimit = loginRateLimit;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        loginRateLimit.verificar(req.email(), http);
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    /** Usuário autenticado atual (endpoint protegido — exige Bearer token válido). */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        return UserResponse.from(user);
    }
}
