package com.autonomousapi.core.security;

import com.autonomousapi.core.error.ApiError;
import com.autonomousapi.core.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
            // V34: completa um login ambíguo — a prova de posse é o pending token (curto,
            // validado por conta própria em AuthService.selectTenant), não um Bearer normal.
            "/v1/auth/select-tenant",
            "/v1/auth/google",
            "/v1/auth/forgot-password",
            "/v1/auth/reset-password",
            "/v1/auth/accept-invite",
            "/v1/auth/accept-team-invite",
            "/v1/auth/refresh",
            "/v1/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // A própria Stripe chama isso, sem JWT — a assinatura HMAC do payload é a segurança.
            "/v1/billing/webhook",
            // A própria Telegram chama isso, sem JWT — o header de secret token (quando
            // configurado) é a segurança (spec 14).
            "/v1/telegram/webhook",
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
                // API REST: requisição não autenticada = 401, papel sem permissão = 403 —
                // achado da spec 15 (equipe/permissões): resposta escrita direto no
                // HttpServletResponse (setStatus + corpo), nunca `sendError`. `sendError`
                // dispara o redirecionamento de página de erro do Tomcat pra "/error", que
                // reentra na cadeia de filtros de segurança — sem Authorization válido nesse
                // dispatch interno, cai de novo no authenticationEntryPoint e SOBRESCREVE o
                // status original. Era por isso que um Despachante barrado por papel (403)
                // recebia 401 — o mesmo bug do "não autenticado" (front desloga e manda pro
                // login) em vez de mostrar "sem permissão". Reproduzido ao vivo antes do
                // fix: response.sendError(403) virava 401 na resposta HTTP de verdade.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        writeApiError(response, HttpServletResponse.SC_UNAUTHORIZED, "not_authenticated", "Não autenticado"))
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        writeApiError(response, HttpServletResponse.SC_FORBIDDEN, "access_denied", "Sem permissão para esta ação")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static void writeApiError(HttpServletResponse response, int status, String code, String message) {
        try {
            response.setStatus(status);
            // getWriter() usa ISO-8859-1 por padrão (default do Servlet spec) se o charset
            // não for setado antes — sem isso, "Não autenticado"/"permissão" saem
            // corrompidos pra qualquer cliente de verdade, não só no terminal.
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(JSON.writeValueAsString(new ApiError(code, message)));
        } catch (java.io.IOException e) {
            // Cliente já desconectou ou stream fechou no meio — nada a fazer, não há mais
            // resposta pra escrever.
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
