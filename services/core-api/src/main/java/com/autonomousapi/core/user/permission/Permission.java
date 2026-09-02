package com.autonomousapi.core.user.permission;

import java.util.Arrays;
import java.util.Optional;

/**
 * Uma permissão concreta = módulo + ação (ADR 0025). Enum achatado (em vez de um par
 * módulo/ação solto) porque é o que vira: nome de authority do Spring no
 * {@code @PreAuthorize}, string persistida em {@code user_permission_override.permission}
 * e chave no JSON da API — ter um único identificador estável pros três evita conversão
 * em cada camada.
 */
public enum Permission {
    FROTA_VER(PermissionModule.FROTA, PermissionAction.VER),
    FROTA_ESCREVER(PermissionModule.FROTA, PermissionAction.ESCREVER),
    ORDENS_SERVICO_VER(PermissionModule.ORDENS_SERVICO, PermissionAction.VER),
    ORDENS_SERVICO_ESCREVER(PermissionModule.ORDENS_SERVICO, PermissionAction.ESCREVER),
    MOTORISTAS_VER(PermissionModule.MOTORISTAS, PermissionAction.VER),
    MOTORISTAS_ESCREVER(PermissionModule.MOTORISTAS, PermissionAction.ESCREVER),
    MENSAGENS_VER(PermissionModule.MENSAGENS, PermissionAction.VER),
    MENSAGENS_ESCREVER(PermissionModule.MENSAGENS, PermissionAction.ESCREVER),
    ROTAS_VER(PermissionModule.ROTAS, PermissionAction.VER),
    ROTAS_ESCREVER(PermissionModule.ROTAS, PermissionAction.ESCREVER),
    CUSTOS_VER(PermissionModule.CUSTOS, PermissionAction.VER),
    CUSTOS_ESCREVER(PermissionModule.CUSTOS, PermissionAction.ESCREVER),
    RELATORIOS_VER(PermissionModule.RELATORIOS, PermissionAction.VER),
    RELATORIOS_ESCREVER(PermissionModule.RELATORIOS, PermissionAction.ESCREVER),
    PARCEIROS_VER(PermissionModule.PARCEIROS, PermissionAction.VER),
    PARCEIROS_ESCREVER(PermissionModule.PARCEIROS, PermissionAction.ESCREVER),
    RECARGA_VER(PermissionModule.RECARGA, PermissionAction.VER),
    RECARGA_ESCREVER(PermissionModule.RECARGA, PermissionAction.ESCREVER);

    /** Prefixo da authority no Spring Security — mesma ideia do {@code ROLE_} que o
     *  {@code JwtAuthenticationFilter} já concede, pra {@code hasAuthority(...)} funcionar
     *  no {@code @PreAuthorize} sem precisar de PermissionEvaluator customizado. */
    public static final String AUTHORITY_PREFIX = "PERM_";

    private final PermissionModule module;
    private final PermissionAction action;

    Permission(PermissionModule module, PermissionAction action) {
        this.module = module;
        this.action = action;
    }

    public PermissionModule module() {
        return module;
    }

    public PermissionAction action() {
        return action;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** Tolerante a valor desconhecido de propósito: permissão gravada por uma versão mais
     *  nova do sistema (ou removida do catálogo depois) não pode derrubar o login de quem
     *  tem essa linha no banco — é ignorada. */
    public static Optional<Permission> porNome(String nome) {
        return Arrays.stream(values()).filter(p -> p.name().equals(nome)).findFirst();
    }
}
