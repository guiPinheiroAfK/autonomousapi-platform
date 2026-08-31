package com.autonomousapi.core.routeplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.collectionpoint.CollectionPoint;
import com.autonomousapi.core.collectionpoint.CollectionPointRepository;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.error.RoutePlanInvalidException;
import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.passenger.Passenger;
import com.autonomousapi.core.pricing.RouteCostEstimator;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutePlanServiceTest {

    /** Mesmo fuso fixo do RoutePlanService (ADR 0021) — usar aqui em vez de LocalDate.now()
     *  puro evita flakiness perto da meia-noite se o JVM de teste rodar noutro fuso. */
    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private final RoutePlanRepository routePlans = mock(RoutePlanRepository.class);
    private final RouteStopRepository routeStops = mock(RouteStopRepository.class);
    private final RoutePlanEventRepository routePlanEvents = mock(RoutePlanEventRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final CollectionPointRepository collectionPoints = mock(CollectionPointRepository.class);
    private final com.autonomousapi.core.passenger.PassengerRepository passengers =
            mock(com.autonomousapi.core.passenger.PassengerRepository.class);
    private final CurrentDriverResolver driverResolver = mock(CurrentDriverResolver.class);

    // Instâncias reais, não mock: GeoApiClient aponta para uma porta que não existe, então
    // toda chamada degrada para "indisponível" (mesmo comportamento de produção com o
    // geo-api fora do ar) e RouteMatrixService cai no fallback haversine — exercita o
    // caminho real de fallback em vez de simular com mocks o que já é testado em
    // OsrmRoutingClient/geo-api. OrToolsRouteOptimizer precisa da libs nativa carregada
    // manualmente aqui porque @PostConstruct só roda dentro do contexto Spring.
    private final GeoApiClient geoApiClient = new GeoApiClient("http://localhost:1", "test-token");
    private final RouteMatrixService routeMatrix = new RouteMatrixService(geoApiClient);
    private final OrToolsRouteOptimizer optimizer = new OrToolsRouteOptimizer();

    {
        optimizer.carregarBibliotecaNativa();
    }

    private final RouteCostEstimator costEstimator = mock(RouteCostEstimator.class);

    private final RoutePlanService service = new RoutePlanService(routePlans, routeStops, routePlanEvents, drivers,
            vehicles, collectionPoints, passengers, driverResolver, routeMatrix, optimizer, costEstimator);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID gestorUserId = UUID.randomUUID();
    private final JwtPrincipal gestorPrincipal = new JwtPrincipal(gestorUserId, tenantId, "GESTOR_FROTA");

    private Driver driver(String nome) {
        return new Driver(tenantId, nome, UUID.randomUUID().toString(), null);
    }

    private RoutePlan routePlan(UUID driverId) {
        return new RoutePlan(tenantId, gestorUserId, driverId, null, RouteCategoria.ROTA, HOJE, null, null);
    }

    @Test
    void suggestOrderAgrupaColetasAntesDeEntregas() {
        // Entrega mais perto do ponto de partida do que qualquer coleta — se a heurística
        // não agrupasse por tipo, o vizinho-mais-próximo puro colocaria a entrega primeiro.
        StopInput coleta1 = new StopInput(StopType.COLETA, "Coleta 1", -23.561, -46.656, null, null, null, null);
        StopInput coleta2 = new StopInput(StopType.COLETA, "Coleta 2", -23.560, -46.650, null, null, null, null);
        StopInput entrega = new StopInput(StopType.ENTREGA, "Entrega", -23.5615, -46.6561, null, null, null, null);

        List<StopInput> ordenado = service.suggestOrder(gestorPrincipal, List.of(entrega, coleta1, coleta2));

        assertEquals(StopType.COLETA, ordenado.get(0).tipo());
        assertEquals(StopType.COLETA, ordenado.get(1).tipo());
        assertEquals(StopType.ENTREGA, ordenado.get(2).tipo());
    }

    @Test
    void suggestOrderResolveCaminhoMaisCurtoComOrToolsSobreFallbackHaversine() {
        // Três coletas em linha reta, começando por A: a ordem mais curta é A -> B -> C, não
        // A -> C -> B (que passaria por B duas vezes na prática). Geo-api indisponível nos
        // testes força o fallback haversine (ver bloco de instâncias reais acima) — este
        // teste garante que o OR-Tools resolve corretamente até sobre essa matriz de fallback.
        StopInput a = new StopInput(StopType.COLETA, "A", -23.000, -46.000, null, null, null, null);
        StopInput b = new StopInput(StopType.COLETA, "B", -23.010, -46.000, null, null, null, null);
        StopInput c = new StopInput(StopType.COLETA, "C", -23.020, -46.000, null, null, null, null);

        List<StopInput> ordenado = service.suggestOrder(gestorPrincipal, List.of(a, c, b));

        assertEquals("A", ordenado.get(0).label());
        assertEquals("B", ordenado.get(1).label());
        assertEquals("C", ordenado.get(2).label());
    }

    @Test
    void suggestOrderRejeitaMaisDeTrintaParadas() {
        List<StopInput> paradas = java.util.stream.IntStream.range(0, 31)
                .mapToObj(i -> new StopInput(StopType.COLETA, "Parada " + i, -23.0 - i * 0.001, -46.0, null, null, null, null))
                .toList();

        assertThrows(RoutePlanInvalidException.class, () -> service.suggestOrder(gestorPrincipal, paradas));
    }

    @Test
    void createRejeitaDataDeExecucaoNoPassado() {
        StopInput s = new StopInput(StopType.COLETA, "Parada", -23.5, -46.6, null, null, null, null);
        LocalDate ontem = HOJE.minusDays(1);

        assertThrows(RoutePlanInvalidException.class,
                () -> service.create(gestorPrincipal, null, null, RouteCategoria.ROTA, ontem, null, List.of(s), null));
    }

    @Test
    void createRejeitaTransferComNumeroDeParadasDiferenteDeDois() {
        StopInput s = new StopInput(StopType.COLETA, "Origem", -23.5, -46.6, null, null, null, null);

        assertThrows(RoutePlanInvalidException.class,
                () -> service.create(
                        gestorPrincipal, null, null, RouteCategoria.TRANSFER, HOJE, null, List.of(s), null));
    }

    @Test
    void createPersisteCustoEstimadoParaTransferComVeiculo() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "Fiat", "Fiorino", 2022, 1000);
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(costEstimator.estimar(eq(tenantId), eq(vehicle), anyDouble())).thenReturn(
                Optional.of(new RouteCostEstimator.Estimate(new BigDecimal("42.50"), new BigDecimal("51.00"))));
        when(routePlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(any())).thenReturn(List.of());

        StopInput origem = new StopInput(StopType.COLETA, "Origem", -23.5, -46.6, null, null, null, null);
        StopInput destino = new StopInput(StopType.ENTREGA, "Destino", -23.6, -46.7, null, null, null, null);

        RoutePlanResponse resp = service.create(gestorPrincipal, null, vehicleId, RouteCategoria.TRANSFER,
                HOJE, new BigDecimal("100.00"), List.of(origem, destino), null);

        assertEquals(new BigDecimal("42.50"), resp.custoEstimado());
    }

    @Test
    void createNaoCalculaCustoEstimadoParaRotaMultiParada() {
        // Fórmula v1 é só pra TRANSFER (spec 09) — ROTA multi-parada não tem valor combinado
        // único pra comparar, então custo estimado não se aplica.
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(tenantId, "ABC1234", "Fiat", "Fiorino", 2022, 1000);
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(routePlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(any())).thenReturn(List.of());

        StopInput s = new StopInput(StopType.COLETA, "Parada", -23.5, -46.6, null, null, null, null);

        RoutePlanResponse resp = service.create(gestorPrincipal, null, vehicleId, RouteCategoria.ROTA,
                HOJE, null, List.of(s), null);

        assertEquals(null, resp.custoEstimado());
        org.mockito.Mockito.verifyNoInteractions(costEstimator);
    }

    @Test
    void margemRealizadaSoApareceQuandoRotaConcluida() {
        RoutePlan plan = new RoutePlan(tenantId, gestorUserId, null, null,
                RouteCategoria.TRANSFER, HOJE, new BigDecimal("100.00"), null);
        plan.registrarCustoEstimado(new BigDecimal("40.00"), "v1");

        RoutePlanResponse planejada = RoutePlanResponse.from(plan, null, null, List.of());
        assertEquals(null, planejada.margemRealizada());

        plan.avancarStatus(RoutePlanStatus.EM_ANDAMENTO);
        plan.avancarStatus(RoutePlanStatus.CONCLUIDA);
        RoutePlanResponse concluida = RoutePlanResponse.from(plan, null, null, List.of());
        assertEquals(new BigDecimal("60.00"), concluida.margemRealizada());
    }

    @Test
    void createRejeitaJanelaComFimAntesDoInicio() {
        StopInput s = new StopInput(
                StopType.COLETA, "Parada", -23.5, -46.6, null, LocalTime.of(12, 0), LocalTime.of(10, 0), null);

        assertThrows(RoutePlanInvalidException.class,
                () -> service.create(gestorPrincipal, null, null, RouteCategoria.ROTA, HOJE, null, List.of(s), null));
    }

    @Test
    void createPropagaViagemIdQuandoInformado() {
        // spec 13: o backend só armazena o viagemId que o front manda (gerado lá, na ida) —
        // não gera nada sozinho, e rota avulsa (viagemId null) continua funcionando normal.
        when(routePlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(any())).thenReturn(List.of());
        UUID viagemId = UUID.randomUUID();
        StopInput origem = new StopInput(StopType.COLETA, "Origem", -23.5, -46.6, null, null, null, null);
        StopInput destino = new StopInput(StopType.ENTREGA, "Destino", -23.6, -46.7, null, null, null, null);

        RoutePlanResponse resp = service.create(
                gestorPrincipal, null, null, RouteCategoria.TRANSFER, HOJE, null, List.of(origem, destino), viagemId);

        assertEquals(viagemId, resp.viagemId());
    }

    @Test
    void createResolveLabelLatLonDoCollectionPointIgnorandoOQueOClienteMandou() {
        UUID pontoId = UUID.randomUUID();
        CollectionPoint ponto = new CollectionPoint(
                tenantId, "Depósito", "Rua Real, 100", -23.111, -46.222, LocalTime.of(8, 0), LocalTime.of(18, 0));
        when(collectionPoints.findByIdAndTenantId(pontoId, tenantId)).thenReturn(Optional.of(ponto));
        when(routePlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(any())).thenReturn(List.of());

        // Cliente manda label/lat/lon "errados" de propósito -- servidor deve ignorar e usar
        // o que está cadastrado no CollectionPoint.
        StopInput spoofado = new StopInput(StopType.COLETA, "Endereço forjado", 0.0, 0.0, pontoId, null, null, null);
        StopInput destino = new StopInput(StopType.ENTREGA, "Destino", -23.3, -46.4, null, null, null, null);

        service.create(gestorPrincipal, null, null, RouteCategoria.ROTA, HOJE, null,
                List.of(spoofado, destino), null);

        org.mockito.ArgumentCaptor<RouteStop> captor = org.mockito.ArgumentCaptor.forClass(RouteStop.class);
        org.mockito.Mockito.verify(routeStops, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        RouteStop salvo = captor.getAllValues().stream()
                .filter(rs -> rs.getCollectionPointId() != null)
                .findFirst()
                .orElseThrow();
        assertEquals("Rua Real, 100", salvo.getLabel());
        assertEquals(-23.111, salvo.getLat());
        assertEquals(-46.222, salvo.getLon());
    }

    @Test
    void createVinculaPassageiroJaCadastradoAParada() {
        UUID passengerId = UUID.randomUUID();
        Passenger passageiro = new Passenger(tenantId, "Maria", "+5545999990000");
        when(passengers.findByIdAndTenantId(passengerId, tenantId)).thenReturn(Optional.of(passageiro));
        when(routePlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(any())).thenReturn(List.of());

        StopInput s = new StopInput(StopType.COLETA, "Parada", -23.5, -46.6, null, null, null, passengerId);

        service.create(gestorPrincipal, null, null, RouteCategoria.ROTA, HOJE, null, List.of(s), null);

        org.mockito.ArgumentCaptor<RouteStop> captor = org.mockito.ArgumentCaptor.forClass(RouteStop.class);
        org.mockito.Mockito.verify(routeStops).save(captor.capture());
        assertEquals(passengerId, captor.getValue().getPassengerId());
    }

    @Test
    void createRejeitaPassageiroDeOutroTenant() {
        UUID passengerId = UUID.randomUUID();
        when(passengers.findByIdAndTenantId(passengerId, tenantId)).thenReturn(Optional.empty());
        StopInput s = new StopInput(StopType.COLETA, "Parada", -23.5, -46.6, null, null, null, passengerId);

        assertThrows(NotFoundException.class,
                () -> service.create(gestorPrincipal, null, null, RouteCategoria.ROTA, HOJE, null, List.of(s), null));
    }

    @Test
    void assignDriverEIdempotenteQuandoJaDesignadaAoMesmoMotorista() {
        UUID routePlanId = UUID.randomUUID();
        Driver d = driver("Eduardo");
        RoutePlan plan = routePlan(d.getId());
        when(routePlans.findForUpdateById(routePlanId)).thenReturn(Optional.of(plan));
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
        RoutePlan plan = routePlan(jaDesignado.getId());
        when(routePlans.findForUpdateById(routePlanId)).thenReturn(Optional.of(plan));
        when(drivers.findByIdAndTenantId(outro.getId(), tenantId)).thenReturn(Optional.of(outro));

        assertThrows(RoutePlanAlreadyAssignedException.class,
                () -> service.assignDriver(gestorPrincipal, routePlanId, outro.getId()));
    }

    @Test
    void completeStopRejeitaMotoristaQueNaoEDonoDaRota() {
        UUID stopId = UUID.randomUUID();
        Driver dono = driver("Eduardo");
        Driver outro = driver("Carlos");
        RoutePlan plan = routePlan(dono.getId());
        RouteStop stop = new RouteStop(plan.getId(), StopType.COLETA, "Rua X", -23.5, -46.6, null, null, null, 0, null);
        JwtPrincipal outroMotoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(outroMotoristaPrincipal)).thenReturn(outro);
        when(routeStops.findById(stopId)).thenReturn(Optional.of(stop));
        when(routePlans.findForUpdateById(plan.getId())).thenReturn(Optional.of(plan));

        assertThrows(NotFoundException.class, () -> service.completeStop(outroMotoristaPrincipal, stopId));
    }

    @Test
    void completeStopPrimeiraParadaMoveStatusParaEmAndamento() {
        Driver d = driver("Eduardo");
        RoutePlan plan = routePlan(d.getId());
        RouteStop stop1 = new RouteStop(plan.getId(), StopType.COLETA, "Parada 1", -23.5, -46.6, null, null, null, 0, null);
        RouteStop stop2 = new RouteStop(plan.getId(), StopType.ENTREGA, "Parada 2", -23.6, -46.7, null, null, null, 1, null);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(routeStops.findById(stop1.getId())).thenReturn(Optional.of(stop1));
        when(routePlans.findForUpdateById(plan.getId())).thenReturn(Optional.of(plan));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stop1, stop2));

        service.completeStop(motoristaPrincipal, stop1.getId());

        assertEquals(RoutePlanStatus.EM_ANDAMENTO, plan.getStatus());
    }

    @Test
    void completeStopUltimaParadaMoveStatusParaConcluida() {
        Driver d = driver("Eduardo");
        RoutePlan plan = routePlan(d.getId());
        plan.avancarStatus(RoutePlanStatus.EM_ANDAMENTO);
        RouteStop stop1 = new RouteStop(plan.getId(), StopType.COLETA, "Parada 1", -23.5, -46.6, null, null, null, 0, null);
        stop1.concluir(1);
        RouteStop stop2 = new RouteStop(plan.getId(), StopType.ENTREGA, "Parada 2", -23.6, -46.7, null, null, null, 1, null);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");

        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(routeStops.findById(stop2.getId())).thenReturn(Optional.of(stop2));
        when(routePlans.findForUpdateById(plan.getId())).thenReturn(Optional.of(plan));
        when(routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()))
                .thenReturn(List.of(stop1, stop2));

        service.completeStop(motoristaPrincipal, stop2.getId());

        assertEquals(RoutePlanStatus.CONCLUIDA, plan.getStatus());
    }
}
