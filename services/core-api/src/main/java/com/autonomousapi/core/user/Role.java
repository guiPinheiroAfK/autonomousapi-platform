package com.autonomousapi.core.user;

/** Perfis de usuário (spec 01). Persistido como string na coluna app_user.role. */
public enum Role {
    GESTOR_FROTA,
    MOTORISTA,
    ADMIN,
    PARCEIRO_API
}
