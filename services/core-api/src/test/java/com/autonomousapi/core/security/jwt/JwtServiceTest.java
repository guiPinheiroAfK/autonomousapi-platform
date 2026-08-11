package com.autonomousapi.core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "dev-only-secret-change-me-please-0123456789abcdef";

    @Test
    void emiteEValidaTokenComClaims() {
        JwtService svc = new JwtService(SECRET, 15);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = svc.issueAccessToken(userId, "GESTOR_FROTA", tenantId);
        Claims claims = svc.parse(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("GESTOR_FROTA", claims.get("role", String.class));
        assertEquals(tenantId.toString(), claims.get("tenantId", String.class));
    }

    @Test
    void aceitaTenantNulo() {
        JwtService svc = new JwtService(SECRET, 15);
        UUID userId = UUID.randomUUID();

        String token = svc.issueAccessToken(userId, "ADMIN", null);
        Claims claims = svc.parse(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void rejeitaSegredoCurto() {
        assertThrows(IllegalStateException.class, () -> new JwtService("curto-demais", 15));
    }
}
