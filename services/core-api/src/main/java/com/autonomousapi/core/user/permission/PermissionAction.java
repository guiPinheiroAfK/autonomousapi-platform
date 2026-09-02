package com.autonomousapi.core.user.permission;

/** Ação sobre um {@link PermissionModule} (ADR 0025) — só duas, de propósito: granularidade
 *  por botão vira uma matriz grande demais pra configurar e um catálogo que cresce a cada
 *  funcionalidade nova. */
public enum PermissionAction {
    VER,
    ESCREVER
}
