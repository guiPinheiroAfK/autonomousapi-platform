package com.autonomousapi.core.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.budget.Budget;
import com.autonomousapi.core.budget.BudgetRepository;
import com.autonomousapi.core.budget.BudgetService;
import com.autonomousapi.core.notification.NotificationService;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Orçamento estourado é estado PERSISTENTE (dura o mês inteiro) — diferente de CNH vencendo
 * (AlertPushJobTest), onde a janela de aviso acaba passando sozinha. Esses testes travam
 * exatamente a diferença: sem dedup por transição de patamar, o gestor receberia o mesmo
 * aviso todo dia até o mês virar.
 */
class BudgetAlertJobTest {

    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final BudgetService budgetService = mock(BudgetService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final BudgetAlertJob job = new BudgetAlertJob(budgets, budgetService, users, notificationService);

    private final UUID tenantId = UUID.randomUUID();

    private User gestor() {
        return new User(tenantId, "gestor@teste.com", "hash", Role.GESTOR_FROTA);
    }

    @Test
    void notificaNaTransicaoDe0Para80PorCento() {
        Budget budget = new Budget(tenantId, null, null, new BigDecimal("100.00"));
        when(budgets.findAll()).thenReturn(List.of(budget));
        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("85.00"));
        User gestor = gestor();
        when(users.findAllByTenantIdAndRoleIn(eq(tenantId), any())).thenReturn(List.of(gestor));

        job.run();

        verify(notificationService).notify(eq(gestor.getId()), any(), any(), any(), any());
    }

    @Test
    void naoNotificaDeNovoNoMesmoPatamarEmRunsSeguintes() {
        Budget budget = new Budget(tenantId, null, null, new BigDecimal("100.00"));
        when(budgets.findAll()).thenReturn(List.of(budget));
        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("85.00"));
        when(users.findAllByTenantIdAndRoleIn(eq(tenantId), any())).thenReturn(List.of(gestor()));

        job.run(); // 0 -> 80: notifica
        job.run(); // continua em 85%: já notificado nesse patamar, não repete

        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void notificaDeNovoNaTransicaoDe80Para100PorCento() {
        Budget budget = new Budget(tenantId, null, null, new BigDecimal("100.00"));
        when(budgets.findAll()).thenReturn(List.of(budget));
        when(users.findAllByTenantIdAndRoleIn(eq(tenantId), any())).thenReturn(List.of(gestor()));

        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("85.00"));
        job.run(); // 0 -> 80

        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("110.00"));
        job.run(); // 80 -> 100

        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
    }

    @Test
    void naoNotificaAbaixoDoPatamarDeAviso() {
        Budget budget = new Budget(tenantId, null, null, new BigDecimal("100.00"));
        when(budgets.findAll()).thenReturn(List.of(budget));
        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("50.00"));

        job.run();

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void resetaPatamarENotificaDeNovoAposVirarOPeriodo() throws Exception {
        Budget budget = new Budget(tenantId, null, null, new BigDecimal("100.00"));
        when(budgets.findAll()).thenReturn(List.of(budget));
        when(users.findAllByTenantIdAndRoleIn(eq(tenantId), any())).thenReturn(List.of(gestor()));
        when(budgetService.progressoAtual(budget)).thenReturn(new BigDecimal("85.00"));

        job.run(); // notifica no mês corrente, patamar 80

        // Simula a virada de mês: força periodoReferencia para um mês passado, sem depender
        // de mockar YearMonth.now() (não injetável hoje) — reflection é o jeito mais direto.
        Field periodoReferencia = Budget.class.getDeclaredField("periodoReferencia");
        periodoReferencia.setAccessible(true);
        periodoReferencia.set(budget, "2000-01");

        job.run(); // mês novo: reseta o patamar, 85% notifica de novo

        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
    }
}
