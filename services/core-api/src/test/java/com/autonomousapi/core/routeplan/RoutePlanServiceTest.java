package com.autonomousapi.core.routeplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutePlanServiceTest {

    private final RoutePlanRepository routePlans = mock(RoutePlanRepository.class);
    private final RouteStopRepository routeStops = mock(RouteStopRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final CurrentDriverResolver driverResolver = mock(CurrentDriverResolver.class);

    private final RoutePlanService service =
            new RoutePlanService(routePlans, routeStops, drivers, vehicles, driverResolver);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID gestorUserId = UUID.randomUUID();
    private final JwtPrincipal gestorPrincipal = new JwtPrincipal(gestorUserId, tenantId, "GESTOR_FROTA");

    private Driver driver(String nome) {
        return new Driver(tenantId, nome, UUID.randomUUID().toString(), null);
    }

    @Test
    void suggestOrderAgrupaColetasAntesDeEntregas() {
        // Entrega mais perto do ponto de partida do que qualquer coleta — se a heurística
        // não agrupasse por tipo, o vizinho-mais-próximo puro colocaria a entrega primeiro.
        StopInput coleta1 = new StopInput(StopType.COLETA, "Coleta 1", -23.561, -46.656, null, null);
        StopInput coleta2 = new StopInput(StopType.COLETA, "Coleta 2", -23.560, -46.650, null, null);
        StopInput entrega = new StopInput(StopType.ENTREGA, "Entrega", -23.5615, -46.6561, null, null);

        List<StopInput> ordenado = service.suggestOrder(List.of(entrega, coleta1, coleta2));

        assertEquals(StopType.COLETA, ordenado.get(0).tipo());
        assertEquals(StopType.COLETA, ordenado.get(1).tipo());
        assertEquals(StopType.ENTREGA, ordenado.get(2).tipo());
    }

    @Test
    void assignDriverEIdempotenteQuandoJaDesignadaAoMesmoMotorista() {
        UUID routePlanId = UUID.randomUUID();
        Driver d = driver("Eduardo");
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, d.getId(), null);
        when(routePlans.findByIdAndTenantId(routePlanId, tenantId)).thenReturn(Optional.of(plan));
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId())).thenReturn(List.of());

        RoutePlanResponse resp = service.assignDriver(gestorPrincipal, routePlanId, d.getId());

        assertEquals(d.getId(), resp.driverId());
    }

    @Test
    void assignDriverRejeitaReatribuicaoParaMotoristaDiferente() {
        UUID routePlanId = UUID.randomUUID();
        Driver jaDesignado = driver("Eduardo");
        Driver outro = driver("Carlos");
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, jaDesignado.getId(), null);
        when(routePlans.findByIdAndTenantId(routePlanId, tenantId)).thenReturn(Optional.of(plan));
        when(drivers.findByIdAndTenantId(outro.getId(), tenantId)).thenReturn(Optional.of(outro));

        assertThrows(RoutePlanAlreadyAssignedException.class,
                () -> service.assignDriver(gestorPrincipal, routePlanId, outro.getId()));
    }

    @Test
    void completeStopRejeitaMotoristaQueNaoEDonoDaRota() {
        UUID stopId = UUID.randomUUID();
        Driver dono = driver("Eduardo");
        Driver outro = driver("Carlos");
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, dono.getId(), null);
        RouteStop stop = new RouteStop(plan.getId(), StopType.COLETA, "Rua X", -23.5, -46.6, null, null, 0);
        JwtPrincipal outroMotoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(outroMotoristaPrincipal)).thenReturn(outro);
        when(routeStops.findById(stopId)).thenReturn(Optional.of(stop));
        when(routePlans.findById(plan.getId())).thenReturn(Optional.of(plan));

        assertThrows(NotFoundException.class, () -> service.completeStop(outroMotoristaPrincipal, stopId));
    }

    @Test
    void completeStopPrimeiraParadaMoveStatusParaEmAndamento() {
        Driver d = driver("Eduardo");
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, d.getId(), null);
        RouteStop stop1 = new RouteStop(plan.getId(), StopType.COLETA, "Parada 1", -23.5, -46.6, null, null, 0);
        RouteStop stop2 = new RouteStop(plan.getId(), StopType.ENTREGA, "Parada 2", -23.6, -46.7, null, null, 1);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(routeStops.findById(stop1.getId())).thenReturn(Optional.of(stop1));
        when(routePlans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stop1, stop2));

        service.completeStop(motoristaPrincipal, stop1.getId());

        assertEquals(RoutePlanStatus.EM_ANDAMENTO, plan.getStatus());
    }

    @Test
    void completeStopUltimaParadaMoveStatusParaConcluida() {
        Driver d = driver("Eduardo");
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, d.getId(), null);
        plan.avancarStatus(RoutePlanStatus.EM_ANDAMENTO);
        RouteStop stop1 = new RouteStop(plan.getId(), StopType.COLETA, "Parada 1", -23.5, -46.6, null, null, 0);
        stop1.concluir(1);
        RouteStop stop2 = new RouteStop(plan.getId(), StopType.ENTREGA, "Parada 2", -23.6, -46.7, null, null, 1);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(routeStops.findById(stop2.getId())).thenReturn(Optional.of(stop2));
        when(routePlans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stop1, stop2));

        service.completeStop(motoristaPrincipal, stop2.getId());

        assertEquals(RoutePlanStatus.CONCLUIDA, plan.getStatus());
    }
}
