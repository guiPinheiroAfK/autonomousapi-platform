package com.autonomousapi.core.team.dto;

import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.permission.Permission;
import com.autonomousapi.core.user.permission.PermissionAction;
import com.autonomousapi.core.user.permission.PermissionModule;
import com.autonomousapi.core.user.permission.RolePermissionDefaults;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de permissão de um membro (ADR 0025) — devolve o catálogo INTEIRO, não só o que
 * está ligado: a tela precisa desenhar todas as caixas, e {@code padraoDoPapel} deixa
 * explícito o que é herdado do papel e o que o gestor ajustou à mão.
 */
public record TeamMemberPermissionsResponse(
        UUID userId, Role role, boolean ajustavel, List<Item> permissoes) {

    public record Item(
            String permissao,
            PermissionModule modulo,
            PermissionAction acao,
            boolean concedida,
            boolean padraoDoPapel) {}

    public static TeamMemberPermissionsResponse of(UUID userId, Role role, Set<Permission> efetivas) {
        Set<Permission> padrao = RolePermissionDefaults.forRole(role);
        List<Item> itens = List.of(Permission.values()).stream()
                .map(p -> new Item(p.name(), p.module(), p.action(), efetivas.contains(p), padrao.contains(p)))
                .toList();
        return new TeamMemberPermissionsResponse(userId, role, RolePermissionDefaults.permiteAjuste(role), itens);
    }
}
