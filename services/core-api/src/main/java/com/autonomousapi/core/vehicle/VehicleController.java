package com.autonomousapi.core.vehicle;

import com.autonomousapi.core.expense.ExpenseEntryService;
import com.autonomousapi.core.expense.dto.MonthlyCostResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import com.autonomousapi.core.vehicle.dto.VehicleRequest;
import com.autonomousapi.core.vehicle.dto.VehicleResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD de veículos (spec 05, Fase 1). Leitura: qualquer usuário autenticado do tenant. */
@RestController
@RequestMapping("/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final ExpenseEntryService expenseService;

    public VehicleController(VehicleService vehicleService, ExpenseEntryService expenseService) {
        this.vehicleService = vehicleService;
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest req, Authentication auth) {
        return vehicleService.create(principal(auth), req);
    }

    @GetMapping
    public List<VehicleResponse> list(Authentication auth) {
        return vehicleService.list(principal(auth));
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable UUID id, Authentication auth) {
        return vehicleService.get(principal(auth), id);
    }

    /** Veículos com manutenção vencida ou a vencer (alerta, spec 05 Fase 1). */
    @GetMapping("/maintenance-due")
    public List<VehicleMaintenanceAlertResponse> maintenanceDue(Authentication auth) {
        return vehicleService.maintenanceDue(principal(auth));
    }

    /** Custo somado por mês, últimos 6 meses, em toda a frota (gráfico de tendência do dashboard). */
    @GetMapping("/cost-trend")
    public List<MonthlyCostResponse> costTrend(Authentication auth) {
        return expenseService.monthlyTrend(principal(auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public VehicleResponse update(
            @PathVariable UUID id, @Valid @RequestBody VehicleRequest req, Authentication auth) {
        return vehicleService.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        vehicleService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
