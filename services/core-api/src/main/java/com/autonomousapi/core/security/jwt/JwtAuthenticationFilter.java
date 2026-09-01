package com.autonomousapi.core.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extrai o Bearer token, valida via {@link JwtService} e popula o SecurityContext.
 * A autorização por perfil sai da claim "role" — sem ida ao banco por requisição.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                if ("pending_login".equals(claims.get("purpose", String.class))) {
                    // V34: token de escolha de tenant no login — nunca autentica requisição
                    // nenhuma, só serve pra POST /v1/auth/select-tenant (que o valida por
                    // conta própria, não via SecurityContext).
                    chain.doFilter(request, response);
                    return;
                }
                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.get("role", String.class);
                String tenantIdClaim = claims.get("tenantId", String.class);
                UUID tenantId = tenantIdClaim == null ? null : UUID.fromString(tenantIdClaim);

                var principal = new JwtPrincipal(userId, tenantId, role);
                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token inválido/expirado: segue sem autenticar; endpoints protegidos devolvem 401.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
