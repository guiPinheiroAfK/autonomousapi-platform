package com.autonomousapi.core.workorder;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.workorder.dto.WorkOrderRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ordens de serviço (manutenção/oficina). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/work-orders")
public class WorkOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ORDENS_SERVICO_ESCREVER')")
    public WorkOrderResponse create(@Valid @RequestBody WorkOrderRequest req, Authentication auth) {
        return workOrderService.create(principal(auth), req);
    }

    @PreAuthorize("hasAuthority('PERM_ORDENS_SERVICO_VER')")
    @GetMapping
    public PageResponse<WorkOrderResponse> list(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(
                workOrderService.list(principal(auth), vehicleId, PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ORDENS_SERVICO_ESCREVER')")
    public WorkOrderResponse update(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderRequest req, Authentication auth) {
        return workOrderService.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ORDENS_SERVICO_ESCREVER')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        workOrderService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
