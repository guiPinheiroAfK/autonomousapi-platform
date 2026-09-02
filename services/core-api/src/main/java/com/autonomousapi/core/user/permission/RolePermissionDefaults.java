package com.autonomousapi.core.user.permission;

import com.autonomousapi.core.user.Role;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Conjunto de permissões que cada papel concede por padrão (ADR 0025). O papel continua
 * existindo como "modelo pronto" escolhido no convite; a permissão por usuário só ajusta
 * por cima disso ({@link UserPermissionService}).
 *
 * <p><b>Os padrões abaixo reproduzem exatamente o comportamento anterior à ADR 0025</b> —
 * quem não mexer em permissão nenhuma não percebe diferença: Despachante segue lendo tudo
 * e escrevendo só em rota (spec 15), Visualizador segue só lendo.
 */
public final class RolePermissionDefaults {

    private RolePermissionDefaults() {}

    private static final Set<Permission> TODAS = EnumSet.allOf(Permission.class);

    private static final Set<Permission> TODAS_DE_LEITURA = EnumSet.copyOf(
            Arrays.stream(Permission.values()).filter(p -> p.action() == PermissionAction.VER).toList());

    public static Set<Permission> forRole(Role role) {
        return switch (role) {
            // Dono da conta: tudo, e não é editável (ver UserPermissionService).
            case GESTOR_FROTA, ADMIN -> EnumSet.copyOf(TODAS);
            // Spec 15: lê tudo, escreve só em rota. MENSAGENS_ESCREVER porque o chat de
            // equipe (ADR 0023) já era aberto a qualquer membro — sem isso, a ADR 0025
            // tiraria um acesso que essas pessoas têm hoje.
            case DESPACHANTE -> comEscrita(PermissionModule.ROTAS, PermissionModule.MENSAGENS);
            case VISUALIZADOR -> comEscrita(PermissionModule.MENSAGENS);
            // Motorista quase não toca nos módulos do painel (tem endpoints próprios em
            // /v1/me e /v1/trips), mas o app dele consome quatro coisas pelos MESMOS
            // endpoints do painel — chat, lista de veículos e pontos de recarga. Sem estas
            // quatro permissões, fechar os GETs que hoje estão sem guarda derrubaria o app
            // do motorista (conferido em apps/mobile/src/api/client.ts).
            case MOTORISTA -> EnumSet.of(
                    Permission.MENSAGENS_VER,
                    Permission.MENSAGENS_ESCREVER,
                    Permission.FROTA_VER,
                    Permission.RECARGA_VER);
            case PARCEIRO_API -> EnumSet.noneOf(Permission.class);
        };
    }

    private static Set<Permission> comEscrita(PermissionModule... modulosComEscrita) {
        Set<Permission> permissoes = EnumSet.copyOf(TODAS_DE_LEITURA);
        for (PermissionModule modulo : modulosComEscrita) {
            Arrays.stream(Permission.values())
                    .filter(p -> p.module() == modulo && p.action() == PermissionAction.ESCREVER)
                    .forEach(permissoes::add);
        }
        return permissoes;
    }

    /** Papéis cujas permissões o gestor pode ajustar na tela de Equipe — dono da conta não
     *  entra (não faz sentido tirar acesso de quem paga a conta), motorista também não
     *  (não é membro de equipe, é operador do app). */
    public static boolean permiteAjuste(Role role) {
        return role == Role.DESPACHANTE || role == Role.VISUALIZADOR;
    }
}
