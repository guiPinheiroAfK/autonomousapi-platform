package com.autonomousapi.core.auth.dto;

import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.permission.Permission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        UUID tenantId,
        /** ADR 0025 — permissões efetivas, pro front esconder o que a pessoa não pode fazer.
         *  É a mesma lista que já viaja no JWT; o backend não confia nela vinda daqui, isso
         *  aqui existe só pra UI não oferecer botão que ia responder 403. */
        List<String> permissions) {

    public static UserResponse from(User user, Set<Permission> permissions) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getTenantId(),
                permissions.stream().map(Permission::name).sorted().toList());
    }
}
