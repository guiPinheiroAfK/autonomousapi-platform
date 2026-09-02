package com.autonomousapi.core.team;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.team.dto.ChangeTeamRoleRequest;
import com.autonomousapi.core.team.dto.CreateTeamInviteRequest;
import com.autonomousapi.core.team.dto.TeamInviteResponse;
import com.autonomousapi.core.team.dto.TeamMemberPermissionsResponse;
import com.autonomousapi.core.team.dto.TeamMemberResponse;
import com.autonomousapi.core.team.dto.TeamOverviewResponse;
import com.autonomousapi.core.team.dto.UpdateTeamPermissionsRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Equipe e permissões (spec 15) — Gestor-only: convidar, listar, mudar papel, remover. */
@RestController
@RequestMapping("/v1/team")
@PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamInviteResponse invite(@Valid @RequestBody CreateTeamInviteRequest req, Authentication auth) {
        return teamService.invite(principal(auth), req);
    }

    @GetMapping
    public TeamOverviewResponse overview(Authentication auth) {
        return teamService.overview(principal(auth));
    }

    @PutMapping("/{userId}/role")
    public TeamMemberResponse changeRole(
            @PathVariable UUID userId, @Valid @RequestBody ChangeTeamRoleRequest req, Authentication auth) {
        return teamService.changeRole(principal(auth), userId, req.role());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID userId, Authentication auth) {
        teamService.remove(principal(auth), userId);
    }

    /** ADR 0025 — catálogo de permissões do membro com o estado atual de cada uma. */
    @GetMapping("/{userId}/permissions")
    public TeamMemberPermissionsResponse permissions(@PathVariable UUID userId, Authentication auth) {
        return teamService.permissoes(principal(auth), userId);
    }

    /** ADR 0025 — recebe o estado final desejado das caixas; o servidor guarda só o que
     *  difere do padrão do papel. Vale no próximo access token do membro (≤15 min). */
    @PutMapping("/{userId}/permissions")
    public TeamMemberPermissionsResponse updatePermissions(
            @PathVariable UUID userId, @Valid @RequestBody UpdateTeamPermissionsRequest req, Authentication auth) {
        return teamService.atualizarPermissoes(principal(auth), userId, req.permissoes());
    }

    @DeleteMapping("/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvite(@PathVariable UUID inviteId, Authentication auth) {
        teamService.cancelInvite(principal(auth), inviteId);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
