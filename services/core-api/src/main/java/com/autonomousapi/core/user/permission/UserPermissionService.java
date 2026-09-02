package com.autonomousapi.core.user.permission;

import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve a permissão efetiva de um usuário (ADR 0025): padrão do papel, ajustado pelos
 * overrides gravados. Chamado na emissão do token ({@code AuthService.issueTokens}) — o
 * resultado viaja dentro do JWT como authority, então nenhum request precisa vir aqui.
 *
 * <p>Consequência assumida: mudança de permissão só vale quando o access token renova (até
 * 15 min). É o mesmo comportamento que remover alguém da equipe já tinha antes desta ADR —
 * o filtro de autenticação nunca foi ao banco por request, e manter isso é justamente o
 * que evita transformar autorização granular em uma consulta a mais em toda requisição.
 */
@Service
public class UserPermissionService {

    private final UserPermissionOverrideRepository overrides;

    public UserPermissionService(UserPermissionOverrideRepository overrides) {
        this.overrides = overrides;
    }

    @Transactional(readOnly = true)
    public Set<Permission> effectiveFor(User user) {
        return effectiveFor(user.getId(), user.getRole());
    }

    @Transactional(readOnly = true)
    public Set<Permission> effectiveFor(UUID userId, Role role) {
        Set<Permission> efetivas = EnumSet.copyOf(RolePermissionDefaults.forRole(role));
        // Dono da conta não tem override aplicável — nem lemos o banco: a tela de Equipe já
        // recusa ajustar quem não é Despachante/Visualizador, isso aqui é a mesma regra do
        // lado de dentro, pra um override antigo (papel promovido depois) não tirar acesso
        // de quem paga a conta.
        if (!RolePermissionDefaults.permiteAjuste(role)) {
            return efetivas;
        }
        for (UserPermissionOverride override : overrides.findAllByUserId(userId)) {
            if (override.isAllowed()) {
                efetivas.add(override.getPermission());
            } else {
                efetivas.remove(override.getPermission());
            }
        }
        return efetivas;
    }

    /** Só o que difere do padrão do papel vira linha — ver {@link UserPermissionOverride}. */
    @Transactional
    public Set<Permission> replaceOverrides(UUID userId, Role role, Set<Permission> desejadas) {
        // EnumSet.copyOf recusa coleção vazia que não seja EnumSet, e "nenhuma permissão" é
        // uma escolha legítima aqui (tirar tudo de alguém) — normaliza antes de usar.
        Set<Permission> alvo = EnumSet.noneOf(Permission.class);
        alvo.addAll(desejadas);
        Set<Permission> padrao = RolePermissionDefaults.forRole(role);
        overrides.deleteAllByUserId(userId);

        List<UserPermissionOverride> novos = EnumSet.allOf(Permission.class).stream()
                .filter(p -> alvo.contains(p) != padrao.contains(p))
                .map(p -> new UserPermissionOverride(userId, p, alvo.contains(p)))
                .toList();
        overrides.saveAll(novos);

        return alvo;
    }
}
