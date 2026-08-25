package com.autonomousapi.core.error;

import java.util.Optional;

/**
 * Helper para o padrão "busca escopada por tenant, 404 se não achar" repetido em ~36 pontos
 * dos services (achado da auditoria de cleanup) — {@code repo.findByIdAndTenantId(id,
 * tenantId).orElseThrow(() -> new NotFoundException(msg))}. Não muda comportamento nenhum,
 * só remove a repetição mecânica do {@code orElseThrow}.
 */
public final class Lookups {

    private Lookups() {}

    public static <T> T orNotFound(Optional<T> found, String message) {
        return found.orElseThrow(() -> new NotFoundException(message));
    }
}
