package com.autonomousapi.core.user;

/** Perfis de usuário (spec 01). Persistido como string na coluna app_user.role. */
public enum Role {
    GESTOR_FROTA,
    MOTORISTA,
    ADMIN,
    PARCEIRO_API,
    /** Papel restrito de equipe (spec 15) — lê tudo que o Gestor lê (exceto Assinatura),
     *  só escreve em rota (criar, sugerir ordem, atribuir motorista). Nunca atribuído no
     *  cadastro/signup — só via convite de equipe, e só o Gestor concede. */
    DESPACHANTE,
    /** Papel restrito de equipe (spec 15) — só leitura, em tudo exceto Assinatura. Papel
     *  padrão de quem aceita um convite de equipe novo. */
    VISUALIZADOR
}
