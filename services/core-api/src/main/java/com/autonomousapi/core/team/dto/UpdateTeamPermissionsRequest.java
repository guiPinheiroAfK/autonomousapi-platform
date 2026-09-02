package com.autonomousapi.core.team.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Conjunto de permissões que o membro deve passar a ter (ADR 0025) — o cliente manda o
 * estado final desejado das caixas, não um diff; quem calcula o que virou override em
 * relação ao padrão do papel é o servidor ({@code UserPermissionService.replaceOverrides}).
 * Lista vazia é válida: significa "sem permissão nenhuma".
 */
public record UpdateTeamPermissionsRequest(@NotNull List<String> permissoes) {}
