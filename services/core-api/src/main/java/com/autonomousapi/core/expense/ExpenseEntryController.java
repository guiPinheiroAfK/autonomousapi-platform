package com.autonomousapi.core.expense;

import com.autonomousapi.core.common.PageResponse;
import com.autonomousapi.core.expense.dto.CategoryTotal;
import com.autonomousapi.core.expense.dto.ExpenseEntryRequest;
import com.autonomousapi.core.expense.dto.ExpenseEntryResponse;
import com.autonomousapi.core.expense.dto.ExpenseSummaryResponse;
import com.autonomousapi.core.expense.dto.FleetExpenseEntryResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Despesas categorizadas (spec 10). Duas famílias de rota: escopada por veículo
 * (/v1/vehicles/{id}/costs, mantida do custo por km original — spec 05) e fleet-wide
 * (/v1/expenses, nova — alimenta a aba "Custos"). Leitura: qualquer usuário do tenant.
 */
@RestController
@RequestMapping("/v1")
public class ExpenseEntryController {

    /** Mesmo teto de VehicleController — ver comentário lá. */
    private static final int MAX_PAGE_SIZE = 500;

    private final ExpenseEntryService expenseService;

    public ExpenseEntryController(ExpenseEntryService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/vehicles/{vehicleId}/costs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_CUSTOS_ESCREVER')")
    public ExpenseEntryResponse addEntry(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody ExpenseEntryRequest req,
            Authentication auth) {
        return expenseService.create(principal(auth), vehicleId, req);
    }

    @PreAuthorize("hasAuthority('PERM_CUSTOS_VER')")
    @GetMapping("/vehicles/{vehicleId}/costs")
    public List<ExpenseEntryResponse> list(@PathVariable UUID vehicleId, Authentication auth) {
        return expenseService.list(principal(auth), vehicleId);
    }

    @PreAuthorize("hasAuthority('PERM_CUSTOS_VER')")
    @GetMapping("/vehicles/{vehicleId}/cost-summary")
    public ExpenseSummaryResponse summary(@PathVariable UUID vehicleId, Authentication auth) {
        return expenseService.summary(principal(auth), vehicleId);
    }

    @DeleteMapping("/vehicles/{vehicleId}/costs/{costId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_CUSTOS_ESCREVER')")
    public void delete(@PathVariable UUID vehicleId, @PathVariable UUID costId, Authentication auth) {
        expenseService.delete(principal(auth), costId);
    }

    /** Nova despesa, com ou sem veículo (frota) — usada pela aba "Custos" (spec 10). */
    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_CUSTOS_ESCREVER')")
    public ExpenseEntryResponse createFleetExpense(@Valid @RequestBody ExpenseEntryRequest req, Authentication auth) {
        return expenseService.create(principal(auth), null, req);
    }

    /** {@code size} limitado a {@value #MAX_PAGE_SIZE} — mesma razão do /v1/vehicles: sob
     *  carga, 10 mil lançamentos de despesa devolvidos de uma vez era o segundo maior gargalo
     *  no load test (p99 ~140ms, picos de 330-370ms). */
    @PreAuthorize("hasAuthority('PERM_CUSTOS_VER')")
    @GetMapping("/expenses")
    public PageResponse<FleetExpenseEntryResponse> fleetExpenses(
            @RequestParam(required = false) ExpenseCategory categoria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(expenseService.fleetExpenses(
                principal(auth), categoria, PageRequest.of(Math.max(page, 0), cappedSize)));
    }

    @PreAuthorize("hasAuthority('PERM_CUSTOS_VER')")
    @GetMapping("/expenses/summary")
    public List<CategoryTotal> summaryByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return expenseService.summaryByCategory(principal(auth), from, to);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_CUSTOS_ESCREVER')")
    public void deleteFleetExpense(@PathVariable UUID id, Authentication auth) {
        expenseService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
