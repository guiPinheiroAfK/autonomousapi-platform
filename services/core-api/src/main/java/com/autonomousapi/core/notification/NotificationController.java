package com.autonomousapi.core.notification;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.notification.dto.NotificationResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sino do topbar (web) e a tela "ver todas". Qualquer usuário autenticado só enxerga as
 *  próprias notificações — sem filtro de role, gestor e motorista usam o mesmo endpoint. */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(notificationService.list(principal(auth), PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notificationService.unreadCount(principal(auth)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, Authentication auth) {
        notificationService.markRead(principal(auth), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication auth) {
        notificationService.markAllRead(principal(auth));
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
