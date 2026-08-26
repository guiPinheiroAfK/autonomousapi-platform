package com.autonomousapi.core.routeplan;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.routeplan.dto.AssignDriverRequest;
import com.autonomousapi.core.routeplan.dto.CreateRoutePlanRequest;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.RouteStopResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.routeplan.dto.SuggestOrderRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rota multi-parada (spec 02, spec 07 item 8). Escrita/planejamento é gestor-only; leitura
 *  e conclusão de parada da rota ativa é motorista-only, escopado ao próprio token. */
@RestController
@RequestMapping("/v1/routes/plans")
public class RoutePlanController {

    // 500 (não 100) porque RentabilidadeTab (web, CostsPage.tsx) agrega TRANSFER
    // concluído de todo o histórico pra relatório de receita — paginação baixa aqui
    // truncaria silenciosamente o número mostrado, não só "carregaria menos rápido".
    private static final int MAX_PAGE_SIZE = 500;

    private final RoutePlanService routePlanService;

    public RoutePlanController(RoutePlanService routePlanService) {
        this.routePlanService = routePlanService;
    }

    /** Stateless — não persiste nada, só devolve a sugestão pro gestor revisar. */
    @PostMapping("/suggest-order")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public List<StopInput> suggestOrder(@Valid @RequestBody SuggestOrderRequest req, Authentication auth) {
        return routePlanService.suggestOrder(principal(auth), req.stops());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public RoutePlanResponse create(@Valid @RequestBody CreateRoutePlanRequest req, Authentication auth) {
        return routePlanService.create(
                principal(auth), req.driverId(), req.vehicleId(), req.categoria(), req.dataExecucao(),
                req.valor(), req.stops());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public PageResponse<RoutePlanResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(
                routePlanService.listForGestor(principal(auth), PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public RoutePlanResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignDriverRequest req, Authentication auth) {
        return routePlanService.assignDriver(principal(auth), id, req.driverId());
    }

    /** Cancelamento direto — só funciona pra PLANEJADA (ADR 0021). Rota já EM_ANDAMENTO
     *  devolve 400 explicando que precisa passar pelo chat. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public RoutePlanResponse cancel(@PathVariable UUID id, Authentication auth) {
        return routePlanService.cancel(principal(auth), id);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RoutePlanResponse active(Authentication auth) {
        return routePlanService.activeForDriver(principal(auth));
    }

    @PostMapping("/stops/{stopId}/complete")
    @PreAuthorize("hasRole('MOTORISTA')")
    public RouteStopResponse completeStop(@PathVariable UUID stopId, Authentication auth) {
        return routePlanService.completeStop(principal(auth), stopId);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
