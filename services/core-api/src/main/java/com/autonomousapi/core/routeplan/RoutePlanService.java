package com.autonomousapi.core.routeplan;

import com.autonomousapi.core.collectionpoint.CollectionPoint;
import com.autonomousapi.core.collectionpoint.CollectionPointRepository;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.error.RoutePlanInvalidException;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.RouteStopResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rota multi-parada (spec 02, "Roteamento com múltiplos pontos"; spec 07 item 8). A ordem
 * sugerida é sempre revisada por um humano antes de persistir — {@link #suggestOrder} nunca
 * grava nada, só devolve uma sugestão; quem persiste a ordem final é {@link #create}, com a
 * lista já na ordem que o gestor confirmou na tela.
 */
@Service
public class RoutePlanService {

    /** Raio médio da Terra em km — usado só pelo fallback (haversine) quando o OSRM /table
     *  ou o OR-Tools não estão disponíveis. */
    private static final double RAIO_TERRA_KM = 6371;

    /** Teto de paradas por route_plan (spec 02, "Evolução pendente": "/table cresce O(n²)"). */
    private static final int TETO_PARADAS_ROTA = 30;

    private final RoutePlanRepository routePlans;
    private final RouteStopRepository routeStops;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final CollectionPointRepository collectionPoints;
    private final CurrentDriverResolver driverResolver;
    private final RouteMatrixService routeMatrix;
    private final OrToolsRouteOptimizer optimizer;

    public RoutePlanService(
            RoutePlanRepository routePlans,
            RouteStopRepository routeStops,
            DriverRepository drivers,
            VehicleRepository vehicles,
            CollectionPointRepository collectionPoints,
            CurrentDriverResolver driverResolver,
            RouteMatrixService routeMatrix,
            OrToolsRouteOptimizer optimizer) {
        this.routePlans = routePlans;
        this.routeStops = routeStops;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.collectionPoints = collectionPoints;
        this.driverResolver = driverResolver;
        this.routeMatrix = routeMatrix;
        this.optimizer = optimizer;
    }

    /**
     * Ordem sugerida via matriz de distância real (OSRM {@code /table}) + solver VRP
     * (OR-Tools), como o spec 02 pede ("nunca implementar o solver do zero", "não usar
     * distância em linha reta"). Se o {@code /table} cair ou o OR-Tools não achar solução,
     * cai para nearest-neighbor por haversine (v1) como último recurso — sempre registrado
     * via log em {@link RouteMatrixService}, nunca silencioso.
     *
     * <p>Agrupa todas as paradas {@code COLETA} antes de qualquer {@code ENTREGA}: mesmo que
     * o caso de uso real seja "paradas livres" sem relação de carga, agrupar não atrapalha; já
     * se for carga real (coletar X, entregar depois), evita sugerir uma entrega antes da
     * coleta correspondente — fisicamente sem sentido. Não persiste nada; o gestor revisa e
     * reordena livremente antes de confirmar em {@link #create}.
     */
    public List<StopInput> suggestOrder(JwtPrincipal gestorPrincipal, List<StopInput> stops) {
        List<StopInput> resolvidos = resolveStops(gestorPrincipal.tenantId(), stops);
        validarTetoDeParadas(resolvidos);
        List<StopInput> coletas = resolvidos.stream().filter(s -> s.tipo() == StopType.COLETA).toList();
        List<StopInput> entregas = resolvidos.stream().filter(s -> s.tipo() == StopType.ENTREGA).toList();
        List<StopInput> ordenado = new ArrayList<>(otimizarGrupo(coletas));
        ordenado.addAll(otimizarGrupo(entregas));
        return ordenado;
    }

    /** Resolve a ordem de um grupo (coletas ou entregas) via matriz real + OR-Tools; cai para
     *  nearest-neighbor por haversine se o solver não devolver uma solução válida. */
    private List<StopInput> otimizarGrupo(List<StopInput> grupo) {
        if (grupo.size() <= 1) {
            return grupo;
        }
        RouteMatrixService.Matriz matriz = routeMatrix.obter(grupo);
        List<Integer> ordem = optimizer.ordenar(matriz.distanciasM());
        if (ordem == null || ordem.size() != grupo.size()) {
            return nearestNeighbor(grupo);
        }
        return ordem.stream().map(grupo::get).toList();
    }

    /**
     * Resolve cada {@link StopInput} que veio com {@code collectionPointId}: label/lat/lon
     * passam a vir do {@link CollectionPoint} cadastrado, nunca do que o cliente mandou
     * (evita drift entre cadastro e parada, e spoofing de coordenada). Janela do request,
     * se preenchida, sobrescreve a janela padrão do ponto só nessa instância.
     */
    private List<StopInput> resolveStops(UUID tenantId, List<StopInput> stops) {
        List<StopInput> resolvidos = new ArrayList<>();
        for (StopInput s : stops) {
            if (s.collectionPointId() == null) {
                if (s.label() == null || s.lat() == null || s.lon() == null) {
                    throw new RoutePlanInvalidException(
                            "Parada sem collectionPointId precisa de label/lat/lon (endereço avulso).");
                }
                resolvidos.add(s);
                continue;
            }
            CollectionPoint ponto = collectionPoints.findByIdAndTenantId(s.collectionPointId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Ponto de coleta não encontrado."));
            resolvidos.add(new StopInput(
                    s.tipo(),
                    ponto.getEndereco(),
                    ponto.getLat(),
                    ponto.getLon(),
                    ponto.getId(),
                    s.janelaInicio() != null ? s.janelaInicio() : ponto.getJanelaInicio(),
                    s.janelaFim() != null ? s.janelaFim() : ponto.getJanelaFim()));
        }
        return resolvidos;
    }

    /** Validações de negócio (spec 02) — nunca só no front. */
    private void validar(RouteCategoria categoria, LocalDate dataExecucao, List<StopInput> stops) {
        validarTetoDeParadas(stops);
        if (dataExecucao.isBefore(LocalDate.now())) {
            throw new RoutePlanInvalidException("Data de execução não pode ser no passado.");
        }
        for (StopInput s : stops) {
            if (s.janelaInicio() != null && s.janelaFim() != null && !s.janelaFim().isAfter(s.janelaInicio())) {
                throw new RoutePlanInvalidException(
                        "Janela de horário inválida: fim precisa ser depois do início (" + s.label() + ").");
            }
        }
        if (categoria == RouteCategoria.TRANSFER) {
            if (stops.size() != 2) {
                throw new RoutePlanInvalidException("TRANSFER exige exatamente 2 paradas (origem e destino).");
            }
            if (stops.get(0).tipo() != StopType.COLETA || stops.get(1).tipo() != StopType.ENTREGA) {
                throw new RoutePlanInvalidException(
                        "TRANSFER exige a primeira parada como COLETA (origem) e a segunda como ENTREGA (destino).");
            }
        }
    }

    private void validarTetoDeParadas(List<StopInput> stops) {
        if (stops.size() > TETO_PARADAS_ROTA) {
            throw new RoutePlanInvalidException(
                    "Máximo de " + TETO_PARADAS_ROTA + " paradas por rota.");
        }
    }

    /** Último recurso, só quando o OR-Tools não devolve solução (matriz malformada, etc.) —
     *  nearest-neighbor greedy por distância haversine em linha reta, a heurística da v1. */
    private List<StopInput> nearestNeighbor(List<StopInput> grupo) {
        List<StopInput> restante = new ArrayList<>(grupo);
        List<StopInput> ordenado = new ArrayList<>();
        if (restante.isEmpty()) return ordenado;

        StopInput atual = restante.remove(0);
        ordenado.add(atual);
        while (!restante.isEmpty()) {
            StopInput referencia = atual;
            StopInput maisProxima = restante.stream()
                    .min(Comparator.comparingDouble(
                            s -> haversineKm(referencia.lat(), referencia.lon(), s.lat(), s.lon())))
                    .orElseThrow();
            restante.remove(maisProxima);
            ordenado.add(maisProxima);
            atual = maisProxima;
        }
        return ordenado;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return RAIO_TERRA_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** {@code stops} já vem na ordem final confirmada pelo gestor — não reordena de novo. */
    @Transactional
    public RoutePlanResponse create(
            JwtPrincipal gestorPrincipal,
            UUID driverId,
            UUID vehicleId,
            RouteCategoria categoria,
            LocalDate dataExecucao,
            BigDecimal valor,
            List<StopInput> stops) {
        UUID tenantId = gestorPrincipal.tenantId();
        if (driverId != null) {
            drivers.findByIdAndTenantId(driverId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
        }
        if (vehicleId != null) {
            vehicles.findByIdAndTenantId(vehicleId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
        }

        List<StopInput> resolvidos = resolveStops(tenantId, stops);
        validar(categoria, dataExecucao, resolvidos);

        RoutePlan plan = routePlans.save(
                new RoutePlan(tenantId, gestorPrincipal.userId(), driverId, vehicleId, categoria, dataExecucao, valor));
        for (int i = 0; i < resolvidos.size(); i++) {
            StopInput s = resolvidos.get(i);
            routeStops.save(new RouteStop(
                    plan.getId(), s.tipo(), s.label(), s.lat(), s.lon(), s.collectionPointId(),
                    s.janelaInicio(), s.janelaFim(), i));
        }
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<RoutePlanResponse> listForGestor(JwtPrincipal gestorPrincipal) {
        return routePlans.findAllByTenantIdOrderByCreatedAtDesc(gestorPrincipal.tenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Idempotente quando já designada ao mesmo motorista. Se já designada a um motorista
     * diferente, lança {@link RoutePlanAlreadyAssignedException} — nunca sobrescreve
     * silenciosamente (achado da revisão do plano: reatribuição precisa de erro explícito).
     */
    @Transactional
    public RoutePlanResponse assignDriver(JwtPrincipal gestorPrincipal, UUID routePlanId, UUID driverId) {
        RoutePlan plan = routePlans.findByIdAndTenantId(routePlanId, gestorPrincipal.tenantId())
                .orElseThrow(() -> new NotFoundException("Rota não encontrada."));
        drivers.findByIdAndTenantId(driverId, gestorPrincipal.tenantId())
                .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));

        if (plan.getDriverId() != null && !plan.getDriverId().equals(driverId)) {
            throw new RoutePlanAlreadyAssignedException();
        }
        if (plan.getDriverId() == null) {
            plan.designarMotorista(driverId);
        }
        return toResponse(plan);
    }

    /** Rota ativa (PLANEJADA ou EM_ANDAMENTO) do motorista do token — null se não houver. */
    @Transactional(readOnly = true)
    public RoutePlanResponse activeForDriver(JwtPrincipal driverPrincipal) {
        Driver driver = driverResolver.resolve(driverPrincipal);
        return routePlans
                .findAllByDriverIdAndStatusInOrderByCreatedAtDesc(
                        driver.getId(), List.of(RoutePlanStatus.PLANEJADA, RoutePlanStatus.EM_ANDAMENTO))
                .stream()
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * Marca a parada como concluída. Nunca aceita driverId vindo do cliente — a identidade
     * vem só do token (regra não-negociável do spec 07); parada de rota que não é do
     * motorista devolve 404 genérico, não revela que a rota existe.
     *
     * <p>Transição de status 100% automática (mesmo espírito do {@code TripStatus}, que já
     * entra em EM_ANDAMENTO sozinho): primeira parada concluída leva PLANEJADA→EM_ANDAMENTO;
     * última parada pendente concluída leva →CONCLUIDA. Nenhum outro método escreve em
     * {@code route_plan.status}.
     */
    @Transactional
    public RouteStopResponse completeStop(JwtPrincipal driverPrincipal, UUID stopId) {
        Driver driver = driverResolver.resolve(driverPrincipal);
        RouteStop stop = routeStops.findById(stopId).orElseThrow(() -> new NotFoundException("Parada não encontrada."));
        RoutePlan plan = routePlans.findById(stop.getRoutePlanId())
                .orElseThrow(() -> new NotFoundException("Parada não encontrada."));

        if (!driver.getId().equals(plan.getDriverId())) {
            throw new NotFoundException("Parada não encontrada.");
        }
        if (stop.isConcluida()) {
            return RouteStopResponse.from(stop);
        }

        List<RouteStop> todas = routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId());
        int ordemReal = (int) todas.stream().filter(RouteStop::isConcluida).count() + 1;
        stop.concluir(ordemReal);

        if (plan.getStatus() == RoutePlanStatus.PLANEJADA) {
            plan.avancarStatus(RoutePlanStatus.EM_ANDAMENTO);
        }
        boolean todasConcluidas = todas.stream().allMatch(s -> s.getId().equals(stop.getId()) || s.isConcluida());
        if (todasConcluidas) {
            plan.avancarStatus(RoutePlanStatus.CONCLUIDA);
        }
        return RouteStopResponse.from(stop);
    }

    private RoutePlanResponse toResponse(RoutePlan plan) {
        String driverName = plan.getDriverId() != null
                ? drivers.findById(plan.getDriverId()).map(Driver::getName).orElse(null)
                : null;
        String vehiclePlate = plan.getVehicleId() != null
                ? vehicles.findById(plan.getVehicleId()).map(Vehicle::getPlate).orElse(null)
                : null;
        List<RouteStopResponse> stops = routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId()).stream()
                .map(RouteStopResponse::from)
                .toList();
        return RoutePlanResponse.from(plan, driverName, vehiclePlate, stops);
    }
}
