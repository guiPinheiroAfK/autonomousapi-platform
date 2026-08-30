package com.autonomousapi.core.security;

import com.autonomousapi.core.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Rotas públicas: auth (só as que não dependem de sessão — login/signup/etc. provam
     * posse por senha ou token de e-mail, refresh prova por refresh token no corpo), health
     * e a documentação OpenAPI. Todo o resto exige JWT — inclusive {@code /v1/auth/me}, que
     * antes caía no wildcard "/v1/auth/**" por engano: sem token nenhum, a requisição passava
     * pelo authorizeHttpRequests sem barrar, e o controller quebrava com NPE tentando ler
     * `authentication.getName()` de um Authentication nulo, em vez de devolver 401 de verdade.
     */
    private static final String[] PUBLIC = {
            "/v1/auth/signup",
            "/v1/auth/verify-email",
            "/v1/auth/resend-verification",
            "/v1/auth/login",
            "/v1/auth/google",
            "/v1/auth/forgot-password",
            "/v1/auth/reset-password",
            "/v1/auth/accept-invite",
            "/v1/auth/refresh",
            "/v1/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // A própria Stripe chama isso, sem JWT — a assinatura HMAC do payload é a segurança.
            "/v1/billing/webhook",
            // Observabilidade (métricas/health do Actuator) para acompanhar teste de carga
            // local — mesma postura de exposição sem JWT que /v3/api-docs já tem hoje.
            "/actuator/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                // API REST: requisição não autenticada = 401 (não o 403 default do Spring).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
