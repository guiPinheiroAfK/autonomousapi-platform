package com.autonomousapi.core.auth.dto;

import java.util.List;
import java.util.UUID;

/** V34, login com múltiplas contas — devolvido por {@code POST /v1/auth/login} quando a
 *  senha bate em mais de uma conta do e-mail (tenants diferentes). {@code pendingToken} vai
 *  em {@code POST /v1/auth/select-tenant} junto do {@code tenantId} escolhido. */
public record TenantChoiceResponse(String pendingToken, List<TenantOption> tenants) {

    public record TenantOption(UUID tenantId, String tenantName, String role) {
    }
}
