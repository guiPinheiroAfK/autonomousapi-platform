package com.autonomousapi.core.user.permission;

/**
 * Módulo do produto que pode ter acesso concedido/revogado por usuário (ADR 0025).
 *
 * <p>A lista espelha o menu lateral de propósito: é assim que o gestor pensa sobre o
 * sistema ("essa pessoa pode mexer em Rotas?"), não por endpoint ou por tabela. Áreas de
 * dono de conta (Equipe, Assinatura) ficam de fora — não são delegáveis, continuam
 * exigindo {@code GESTOR_FROTA}/{@code ADMIN} direto no {@code @PreAuthorize}.
 */
public enum PermissionModule {
    FROTA,
    /** Ordens de serviço + a tela de Manutenção, que lê a mesma base. */
    ORDENS_SERVICO,
    MOTORISTAS,
    MENSAGENS,
    /** Rotas, coleta e entrega, pontos de coleta e passageiros — o mesmo fluxo operacional. */
    ROTAS,
    CUSTOS,
    RELATORIOS,
    PARCEIROS,
    RECARGA
}
