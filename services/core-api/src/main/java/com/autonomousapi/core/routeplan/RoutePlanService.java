package com.autonomousapi.core.routeplan;

import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.RouteStopResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
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

    /** Raio médio da Terra em km — usado só pela heurística de sugestão (haversine). */
    private static final double RAIO_TERRA_KM = 6371;

    private final RoutePlanRepository routePlans;
    private final RouteStopRepository routeStops;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final CurrentDriverResolver driverResolver;

    public RoutePlanService(
            RoutePlanRepository routePlans,
            RouteStopRepository routeStops,
            DriverRepository drivers,
            VehicleRepository vehicles,
            CurrentDriverResolver driverResolver) {
        this.routePlans = routePlans;
        this.routeStops = routeStops;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.driverResolver = driverResolver;
    }

    /**
     * Heurística vizinho-mais-próximo (greedy nearest-neighbor) por distância haversine em
     * linha reta — v1 deliberadamente simples (spec 02 só proíbe força bruta, não exige
     * matriz de rede desde já). Evolução natural: trocar a distância haversine pela matriz
     * {@code /table} do OSRM + solver (OR-Tools) quando o volume justificar, mesma lógica de
     * faseamento já usada no resto do repo (ADR 0018).
     *
     * <p>Agrupa todas as paradas {@code COLETA} antes de qualquer {@code ENTREGA}: mesmo que
     * o caso de uso real seja "paradas livres" sem relação de carga, agrupar não atrapalha; já
     * se for carga real (coletar X, entregar depois), evita sugerir uma entrega antes da
     * coleta correspondente — fisicamente sem sentido. Não persiste nada; o gestor revisa e
     * reordena livremente antes de confirmar em {@link #create}.
     */
    public List<StopInput> suggestOrder(List<StopInput> stops) {
        List<StopInput> coletas = stops.stream().filter(s -> s.tipo() == StopType.COLETA).toList();
        List<StopInput> entregas = stops.stream().filter(s -> s.tipo() == StopType.ENTREGA).toList();
        List<StopInput> ordenado = new ArrayList<>(nearestNeighbor(coletas));
        ordenado.addAll(nearestNeighbor(entregas));
        return ordenado;
    }

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
    public RoutePlanResponse create(JwtPrincipal gestorPrincipal, UUID driverId, UUID vehicleId, List<StopInput> stops) {
        UUID tenantId = gestorPrincipal.tenantId();
        if (driverId != null) {
            drivers.findByIdAndTenantId(driverId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
        }
        if (vehicleId != null) {
            vehicles.findByIdAndTenantId(vehicleId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
        }

        RoutePlan plan = routePlans.save(new RoutePlan(tenantId, gestorPrincipal.userId(), driverId, vehicleId));
        for (int i = 0; i < stops.size(); i++) {
            StopInput s = stops.get(i);
            routeStops.save(new RouteStop(
                    plan.getId(), s.tipo(), s.label(), s.lat(), s.lon(), s.janelaInicio(), s.janelaFim(), i));
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
