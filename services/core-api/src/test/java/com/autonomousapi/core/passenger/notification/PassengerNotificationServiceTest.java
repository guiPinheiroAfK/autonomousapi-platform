package com.autonomousapi.core.passenger.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.passenger.Passenger;
import com.autonomousapi.core.passenger.PassengerRepository;
import com.autonomousapi.core.routeplan.RouteCategoria;
import com.autonomousapi.core.routeplan.RoutePlan;
import com.autonomousapi.core.routeplan.RouteStop;
import com.autonomousapi.core.routeplan.RouteStopRepository;
import com.autonomousapi.core.routeplan.StopType;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PassengerNotificationServiceTest {

    private final PassengerRepository passengers = mock(PassengerRepository.class);
    private final RouteStopRepository routeStops = mock(RouteStopRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PassengerNotificationSender sender = mock(PassengerNotificationSender.class);
    private final PassengerNotificationService service =
            new PassengerNotificationService(passengers, routeStops, tenants, sender);

    private final UUID tenantId = UUID.randomUUID();
    private final Tenant tenant = tenant(tenantId, "Frota Teste");

    private RoutePlan plan() {
        return new RoutePlan(tenantId, UUID.randomUUID(), UUID.randomUUID(), null,
                RouteCategoria.ROTA, LocalDate.now(), null, null);
    }

    private RouteStop stopComPassageiro(UUID routePlanId, UUID passengerId) {
        return new RouteStop(routePlanId, StopType.ENTREGA, "Casa", -25.5, -54.5, null, null, null, 0, passengerId);
    }

    private static Tenant tenant(UUID id, String name) {
        Tenant t = new Tenant(name);
        // Tenant não expõe construtor com id fixo — usa reflection só pra fixar o id nos
        // testes, mesmo padrão já usado em ChatCleanupJobTest.
        try {
            var f = Tenant.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    @Test
    void notificarConfirmacaoIgnoraRotaSemPassageiro() {
        RoutePlan plan = plan();
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId())).thenReturn(List.of());

        boolean enviou = service.notificarConfirmacao(plan);

        assertFalse(enviou);
        verify(sender, never()).sendMessage(anyLong(), any());
    }

    @Test
    void notificarConfirmacaoPulaPassageiroSemTelegramVinculado() {
        RoutePlan plan = plan();
        UUID passengerId = UUID.randomUUID();
        Passenger semVinculo = new Passenger(tenantId, "Ana", "+5545999990000");
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stopComPassageiro(plan.getId(), passengerId)));
        when(passengers.findAllById(List.of(passengerId))).thenReturn(List.of(semVinculo));

        boolean enviou = service.notificarConfirmacao(plan);

        assertFalse(enviou);
        verify(sender, never()).sendMessage(anyLong(), any());
    }

    @Test
    void notificarConfirmacaoEnviaParaPassageiroVinculadoEIdentificaEmpresa() {
        RoutePlan plan = plan();
        UUID passengerId = UUID.randomUUID();
        Passenger vinculado = new Passenger(tenantId, "Ana", "+5545999990000");
        vinculado.vincularTelegram(555L);
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stopComPassageiro(plan.getId(), passengerId)));
        when(passengers.findAllById(List.of(passengerId))).thenReturn(List.of(vinculado));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        boolean enviou = service.notificarConfirmacao(plan);

        assertTrue(enviou);
        verify(sender).sendMessage(eq(555L), org.mockito.ArgumentMatchers.contains("Frota Teste"));
    }

    @Test
    void notificarCancelamentoNaoEnviaSeRotaNuncaNotificouConfirmacao() {
        RoutePlan plan = plan(); // passageirosNotificados = false por padrão

        service.notificarCancelamento(plan);

        verify(sender, never()).sendMessage(anyLong(), any());
    }

    @Test
    void notificarCancelamentoEnviaQuandoJaTinhaConfirmado() {
        RoutePlan plan = plan();
        plan.marcarPassageirosNotificados();
        UUID passengerId = UUID.randomUUID();
        Passenger vinculado = new Passenger(tenantId, "Ana", "+5545999990000");
        vinculado.vincularTelegram(555L);
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stopComPassageiro(plan.getId(), passengerId)));
        when(passengers.findAllById(List.of(passengerId))).thenReturn(List.of(vinculado));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        service.notificarCancelamento(plan);

        verify(sender).sendMessage(eq(555L), org.mockito.ArgumentMatchers.contains("cancelada"));
    }

    @Test
    void notificarEmbarqueConfirmadoIgnoraParadaSemPassageiro() {
        RoutePlan plan = plan();
        RouteStop semPassageiro = new RouteStop(plan.getId(), StopType.ENTREGA, "Casa", -25.5, -54.5, null, null, null, 0, null);

        service.notificarEmbarqueConfirmado(plan, semPassageiro);

        verify(sender, never()).sendMessage(anyLong(), any());
    }

    @Test
    void notificarManualmenteUsaTextoDeEmbarqueQuandoParadaJaConcluida() {
        RoutePlan plan = plan();
        UUID passengerId = UUID.randomUUID();
        RouteStop stop = stopComPassageiro(plan.getId(), passengerId);
        stop.concluir(1);
        Passenger vinculado = new Passenger(tenantId, "Ana", "+5545999990000");
        vinculado.vincularTelegram(555L);
        when(passengers.findById(passengerId)).thenReturn(Optional.of(vinculado));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        service.notificarManualmente(plan, stop);

        verify(sender).sendMessage(eq(555L), org.mockito.ArgumentMatchers.contains("embarque confirmado"));
    }
}
