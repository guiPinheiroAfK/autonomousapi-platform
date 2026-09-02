package com.autonomousapi.core.security.jwt;

import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.permission.Permission;
import com.autonomousapi.core.user.permission.RolePermissionDefaults;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Emite e valida access tokens JWT (HS256). Stateless: nada de sessão no servidor. */
@Service
public class JwtService {

    /** V34, login com múltiplas contas: janela curta de propósito — só serve pra completar
     *  um login que já teve a senha validada, escolhendo entre os tenants candidatos. */
    private static final Duration PENDING_LOGIN_TTL = Duration.ofMinutes(5);

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret precisa de no mínimo 32 bytes para HS256 (tem " + bytes.length + ").");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    /**
     * ADR 0025: além do papel, o token carrega a lista de permissões efetivas — o filtro as
     * transforma em authorities, então {@code @PreAuthorize} decide sem ir ao banco, como
     * já fazia com o papel. Custo assumido: mudança de permissão só vale no próximo token
     * (até 15 min), igual ao que remover alguém da equipe já fazia.
     */
    public String issueAccessToken(UUID userId, String role, UUID tenantId, Collection<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("tenantId", tenantId == null ? null : tenantId.toString())
                .claim("perms", List.copyOf(permissions))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Atalho para "token com as permissões padrão deste papel" — usado onde não há usuário
     * carregado pra consultar override (e nos testes de autorização por papel). O caminho
     * normal de login passa a lista efetiva explicitamente, ver
     * {@code AuthService.issueTokens}.
     */
    public String issueAccessToken(UUID userId, String role, UUID tenantId) {
        Set<Permission> padrao;
        try {
            padrao = RolePermissionDefaults.forRole(Role.valueOf(role));
        } catch (IllegalArgumentException ex) {
            padrao = Set.of();
        }
        return issueAccessToken(userId, role, tenantId, padrao.stream().map(Permission::name).toList());
    }

    /** Valida a assinatura/expiração e devolve as claims. Lança JwtException se inválido. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * V34, login com múltiplas contas: quando a mesma senha bate em mais de uma conta do
     * e-mail (tenants diferentes), o login não emite tokens de acesso direto — emite este
     * token curto, trocado em {@code POST /v1/auth/select-tenant} por um par de tokens de
     * verdade pro tenant escolhido. A senha já foi validada contra todos os candidatos antes
     * de chegar aqui — possuir este token prova exatamente isso, nada mais fraco que o login
     * normal. Mesma chave/assinatura do access token, só um TTL bem mais curto e uma claim
     * {@code purpose} que {@link JwtAuthenticationFilter} recusa em qualquer endpoint
     * protegido comum.
     */
    public String issuePendingLoginToken(String email, List<UUID> tenantIds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("purpose", "pending_login")
                .claim("tenantIds", tenantIds.stream().map(UUID::toString).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(PENDING_LOGIN_TTL)))
                .signWith(key)
                .compact();
    }

    /** Valida o token de {@link #issuePendingLoginToken} — assinatura/expiração (herdado de
     *  {@link #parse}) e o propósito. Lança JwtException se inválido ou não for desse tipo. */
    public Claims parsePendingLoginToken(String token) {
        Claims claims = parse(token);
        if (!"pending_login".equals(claims.get("purpose", String.class))) {
            throw new io.jsonwebtoken.JwtException("Token não é de escolha de tenant.");
        }
        return claims;
    }
}
