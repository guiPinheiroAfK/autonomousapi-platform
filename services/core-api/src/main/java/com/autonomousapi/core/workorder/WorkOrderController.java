package com.autonomousapi.core.workorder;

import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.workorder.dto.WorkOrderRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ordens de serviço (manutenção/oficina). Leitura: qualquer usuário do tenant. */
@RestController
@RequestMapping("/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public WorkOrderResponse create(@Valid @RequestBody WorkOrderRequest req, Authentication auth) {
        return workOrderService.create(principal(auth), req);
    }

    @GetMapping
    public List<WorkOrderResponse> list(
            @RequestParam(required = false) UUID vehicleId, Authentication auth) {
        return workOrderService.list(principal(auth), vehicleId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public WorkOrderResponse update(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderRequest req, Authentication auth) {
        return workOrderService.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        workOrderService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
