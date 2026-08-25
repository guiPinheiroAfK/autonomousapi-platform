package com.autonomousapi.core.budget;

import com.autonomousapi.core.budget.dto.BudgetRequest;
import com.autonomousapi.core.budget.dto.BudgetResponse;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.expense.ExpenseEntryRepository;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orçamento com alerta de estouro (spec 10, item 2). Leitura/escrita: gestor-only. */
@Service
public class BudgetService {

    private final BudgetRepository budgets;
    private final ExpenseEntryRepository expenses;
    private final VehicleRepository vehicles;

    public BudgetService(BudgetRepository budgets, ExpenseEntryRepository expenses, VehicleRepository vehicles) {
        this.budgets = budgets;
        this.expenses = expenses;
        this.vehicles = vehicles;
    }

    @Transactional
    public BudgetResponse create(JwtPrincipal principal, BudgetRequest req) {
        if (req.vehicleId() != null) {
            Lookups.orNotFound(vehicles.findByIdAndTenantId(req.vehicleId(), principal.tenantId()), "Veículo não encontrado.");
        }
        Budget budget = new Budget(principal.tenantId(), req.vehicleId(), req.categoria(), req.valorLimite());
        budgets.save(budget);
        return toResponse(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(JwtPrincipal principal) {
        return budgets.findAllByTenantIdOrderByCreatedAtDesc(principal.tenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID budgetId) {
        Budget budget = Lookups.orNotFound(budgets.findByIdAndTenantId(budgetId, principal.tenantId()), "Orçamento não encontrado.");
        budgets.delete(budget);
    }

    /** Valor consumido no mês corrente, no escopo do orçamento. Público: usado também por
     *  {@code BudgetAlertJob} (pacote push) pra calcular o percentual antes de notificar. */
    public BigDecimal progressoAtual(Budget budget) {
        YearMonth mesAtual = YearMonth.now();
        return expenses.sumForBudgetScope(
                budget.getTenantId(), budget.getVehicleId(), budget.getCategoria(),
                mesAtual.atDay(1), LocalDate.now());
    }

    private BudgetResponse toResponse(Budget budget) {
        return BudgetResponse.from(budget, progressoAtual(budget));
    }
}
