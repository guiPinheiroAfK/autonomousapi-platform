package com.autonomousapi.core.budget;

import com.autonomousapi.core.budget.dto.BudgetRequest;
import com.autonomousapi.core.budget.dto.BudgetResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Orçamento com alerta de estouro (spec 10, item 2) — parte da tela de Custos. Leitura
 *  aberta aos três papéis de gestão (spec 15: Gestor/Despachante/Visualizador leem tudo
 *  exceto Assinatura); criar/excluir orçamento continua Gestor-only. */
@RestController
@RequestMapping("/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public BudgetResponse create(@Valid @RequestBody BudgetRequest req, Authentication auth) {
        return budgetService.create(principal(auth), req);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN', 'DESPACHANTE', 'VISUALIZADOR')")
    public List<BudgetResponse> list(Authentication auth) {
        return budgetService.list(principal(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        budgetService.delete(principal(auth), id);
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
