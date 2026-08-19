package com.autonomousapi.core.push;

import com.autonomousapi.core.budget.Budget;
import com.autonomousapi.core.budget.BudgetRepository;
import com.autonomousapi.core.budget.BudgetService;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job diário de alerta de orçamento (spec 10, item 2). Diferente de {@link AlertPushJob}
 * (CNH/manutenção, onde a janela de aviso acaba passando sozinha), orçamento estourado é
 * estado PERSISTENTE — dura o mês inteiro — então este job precisa de dedup real: só
 * notifica na TRANSIÇÃO de patamar (0→80%, 80→100%), nunca a cada run, senão o gestor
 * recebe o mesmo aviso todo dia por semanas. Ver {@link Budget#avancarPatamarSeNovo}.
 */
@Component
public class BudgetAlertJob {

    private static final BigDecimal PATAMAR_AVISO = BigDecimal.valueOf(80);
    private static final BigDecimal PATAMAR_ESTOURO = BigDecimal.valueOf(100);

    private final BudgetRepository budgets;
    private final BudgetService budgetService;
    private final UserRepository users;
    private final PushNotificationService pushNotificationService;

    public BudgetAlertJob(
            BudgetRepository budgets,
            BudgetService budgetService,
            UserRepository users,
            PushNotificationService pushNotificationService) {
        this.budgets = budgets;
        this.budgetService = budgetService;
        this.users = users;
        this.pushNotificationService = pushNotificationService;
    }

    /** Todo dia às 08:00 (horário do servidor), junto com o AlertPushJob. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void run() {
        for (Budget budget : budgets.findAll()) {
            budget.garantirPeriodoAtual();

            BigDecimal consumido = budgetService.progressoAtual(budget);
            BigDecimal percentual = budget.getValorLimite().signum() == 0
                    ? BigDecimal.ZERO
                    : consumido.divide(budget.getValorLimite(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

            String patamarAtual = percentual.compareTo(PATAMAR_ESTOURO) >= 0
                    ? "100"
                    : percentual.compareTo(PATAMAR_AVISO) >= 0 ? "80" : null;

            if (patamarAtual != null && budget.avancarPatamarSeNovo(patamarAtual)) {
                notificarGestores(budget, patamarAtual);
            }
            budgets.save(budget);
        }
    }

    private void notificarGestores(Budget budget, String patamar) {
        List<User> gestores = users.findAllByTenantIdAndRoleIn(
                budget.getTenantId(), List.of(Role.GESTOR_FROTA, Role.ADMIN));

        String titulo = patamar.equals("100") ? "Orçamento estourado" : "Orçamento quase no limite";
        String escopo = budget.getVehicleId() != null ? "de um veículo" : "da frota";
        String corpo = patamar.equals("100")
                ? "Um orçamento " + escopo + " já passou de 100% do limite deste mês."
                : "Um orçamento " + escopo + " já passou de 80% do limite deste mês.";

        for (User gestor : gestores) {
            pushNotificationService.notifyUser(gestor.getId(), titulo, corpo);
        }
    }
}
