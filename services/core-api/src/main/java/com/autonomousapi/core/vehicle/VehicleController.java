package com.autonomousapi.core.vehicle;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.expense.ExpenseEntryService;
import com.autonomousapi.core.expense.dto.MonthlyCostResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import com.autonomousapi.core.vehicle.dto.VehicleRequest;
import com.autonomousapi.core.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import java.util.List;
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

/** CRUD de veículos (spec 05, Fase 1). Leitura: qualquer usuário autenticado do tenant. */
@RestController
@RequestMapping("/v1/vehicles")
public class VehicleController {

    /** Alto o bastante pra telas que ainda precisam da frota inteira de uma vez (dashboard,
     *  seletores de veículo em outras telas) conseguirem pedir tudo numa página só na grande
     *  maioria dos tenants, mas ainda um teto — nenhuma request devolve os 2000+ veículos do
     *  load test de uma vez só, que era exatamente o problema original. */
    private static final int MAX_PAGE_SIZE = 500;

    private final VehicleService vehicleService;
    private final ExpenseEntryService expenseService;

    public VehicleController(VehicleService vehicleService, ExpenseEntryService expenseService) {
        this.vehicleService = vehicleService;
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_FROTA_ESCREVER')")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest req, Authentication auth) {
        return vehicleService.create(principal(auth), req);
    }

    /** {@code size} é limitado a {@value #MAX_PAGE_SIZE} pra um cliente não conseguir
     *  contornar a paginação pedindo tudo de uma vez (era exatamente esse o problema:
     *  frota de 2000 veículos devolvida inteira em cada request). {@code search} (placa/
     *  marca/modelo) e {@code status} são opcionais — sem eles eram filtrados em memória
     *  no front sobre a frota inteira; com paginação isso "escondia" veículo fora da
     *  página atual, então o filtro precisou virar server-side junto com a paginação. */
    @PreAuthorize("hasAuthority('PERM_FROTA_VER')")
    @GetMapping
    public PageResponse<VehicleResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(vehicleService.list(
                principal(auth), search, status, PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @PreAuthorize("hasAuthority('PERM_FROTA_VER')")
    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable UUID id, Authentication auth) {
        return vehicleService.get(principal(auth), id);
    }

    /** Veículos com manutenção vencida ou a vencer (alerta, spec 05 Fase 1). */
    @PreAuthorize("hasAuthority('PERM_FROTA_VER')")
    @GetMapping("/maintenance-due")
    public List<VehicleMaintenanceAlertResponse> maintenanceDue(Authentication auth) {
        return vehicleService.maintenanceDue(principal(auth));
    }

    /** Custo somado por mês, últimos 6 meses, em toda a frota (gráfico de tendência do dashboard). */
    @PreAuthorize("hasAuthority('PERM_CUSTOS_VER')")
    @GetMapping("/cost-trend")
    public List<MonthlyCostResponse> costTrend(Authentication auth) {
        return expenseService.monthlyTrend(principal(auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_FROTA_ESCREVER')")
    public VehicleResponse update(
            @PathVariable UUID id, @Valid @RequestBody VehicleRequest req, Authentication auth) {
        return vehicleService.update(principal(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_FROTA_ESCREVER')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        vehicleService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
