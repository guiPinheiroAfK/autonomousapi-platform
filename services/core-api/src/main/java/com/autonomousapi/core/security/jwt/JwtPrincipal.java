package com.autonomousapi.core.security.jwt;

import java.util.UUID;

/**
 * Principal autenticado, decodificado do JWT. Carrega tenantId e role já prontos
 * para evitar ida ao banco em toda requisição (escopo por tenant, spec 01).
 *
 * toString() devolve o userId: preserva o comportamento de Authentication#getName()
 * usado em endpoints existentes (ex. AuthController#me), que esperam essa string.
 */
public record JwtPrincipal(UUID userId, UUID tenantId, String role) {

    @Override
    public String toString() {
        return userId.toString();
    }
}
