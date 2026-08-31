package com.autonomousapi.core.team.dto;

import java.util.List;

public record TeamOverviewResponse(List<TeamMemberResponse> membros, List<TeamInviteResponse> convitesPendentes) {
}
