package com.autonomousapi.core.routeplan;

import com.autonomousapi.core.collectionpoint.CollectionPoint;
import com.autonomousapi.core.collectionpoint.CollectionPointRepository;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.error.RoutePlanInvalidException;
import com.autonomousapi.core.passenger.PassengerRepository;
import com.autonomousapi.core.passenger.notification.PassengerNotificationService;
import com.autonomousapi.core.pricing.RouteCostEstimator;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.routeplan.dto.RouteStopResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** ADR 0021: operação só Brasil por enquanto — fixo em vez de depender do fuso do
     *  container (achado da revisão de código: sem TZ configurado em lugar nenhum, o JVM
     *  caía no padrão do container, tipicamente UTC, rejeitando data de execução legítima
     *  perto da meia-noite em Brasília). */
    private static final ZoneId FUSO_OPERACAO = ZoneId.of("America/Sao_Paulo");

    private final RoutePlanRepository routePlans;
    private final RouteStopRepository routeStops;
    private final RoutePlanEventRepository routePlanEvents;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final CollectionPointRepository collectionPoints;
    private final PassengerRepository passengers;
    private final CurrentDriverResolver driverResolver;
    private final RouteMatrixService routeMatrix;
    private final OrToolsRouteOptimizer optimizer;
    private final RouteCostEstimator costEstimator;
    private final PassengerNotificationService passengerNotifications;

    public RoutePlanService(
            RoutePlanRepository routePlans,
            RouteStopRepository routeStops,
            RoutePlanEventRepository routePlanEvents,
            DriverRepository drivers,
            VehicleRepository vehicles,
            CollectionPointRepository collectionPoints,
            PassengerRepository passengers,
            CurrentDriverResolver driverResolver,
            RouteMatrixService routeMatrix,
            OrToolsRouteOptimizer optimizer,
            RouteCostEstimator costEstimator,
            PassengerNotificationService passengerNotifications) {
        this.routePlans = routePlans;
        this.routeStops = routeStops;
        this.routePlanEvents = routePlanEvents;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.collectionPoints = collectionPoints;
        this.passengers = passengers;
        this.driverResolver = driverResolver;
        this.routeMatrix = routeMatrix;
        this.optimizer = optimizer;
        this.costEstimator = costEstimator;
        this.passengerNotifications = passengerNotifications;
    }

    private void registrarEvento(UUID routePlanId, RoutePlanEventType tipo, UUID atorUserId, Map<String, Object> metadado) {
        routePlanEvents.save(new RoutePlanEvent(routePlanId, tipo, atorUserId, metadado));
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
            if (s.passengerId() != null) {
                Lookups.orNotFound(
                        passengers.findByIdAndTenantId(s.passengerId(), tenantId), "Passageiro não encontrado.");
            }
            if (s.collectionPointId() == null) {
                if (s.label() == null || s.lat() == null || s.lon() == null) {
                    throw new RoutePlanInvalidException(
                            "Parada sem collectionPointId precisa de label/lat/lon (endereço avulso).");
                }
                resolvidos.add(s);
                continue;
            }
            CollectionPoint ponto = Lookups.orNotFound(
                    collectionPoints.findByIdAndTenantId(s.collectionPointId(), tenantId), "Ponto de coleta não encontrado.");
            resolvidos.add(new StopInput(
                    s.tipo(),
                    ponto.getEndereco(),
                    ponto.getLat(),
                    ponto.getLon(),
                    ponto.getId(),
                    s.janelaInicio() != null ? s.janelaInicio() : ponto.getJanelaInicio(),
                    s.janelaFim() != null ? s.janelaFim() : ponto.getJanelaFim(),
                    s.passengerId()));
        }
        return resolvidos;
    }

    /** Validações de negócio (spec 02) — nunca só no front. */
    private void validar(RouteCategoria categoria, LocalDate dataExecucao, List<StopInput> stops) {
        validarTetoDeParadas(stops);
        if (dataExecucao.isBefore(LocalDate.now(FUSO_OPERACAO))) {
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

    /**
     * Distância origem→destino via a mesma matriz real (OSRM /table, com fallback haversine)
     * usada em {@link #suggestOrder} — reaproveita {@link RouteMatrixService} em vez de abrir
     * um segundo caminho de cálculo de distância só para custo estimado.
     */
    private java.util.Optional<RouteCostEstimator.Estimate> calcularCustoEstimado(
            UUID tenantId, Vehicle vehicle, List<StopInput> paradasTransfer) {
        RouteMatrixService.Matriz matriz = routeMatrix.obter(paradasTransfer);
        double distanciaKm = matriz.distanciasM()[0][1] / 1000.0;
        return costEstimator.estimar(tenantId, vehicle, distanciaKm);
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
            List<StopInput> stops,
            UUID viagemId) {
        UUID tenantId = gestorPrincipal.tenantId();
        if (driverId != null) {
            Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, tenantId), "Motorista não encontrado.");
        }
        Vehicle vehicle = null;
        if (vehicleId != null) {
            vehicle = Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, tenantId), "Veículo não encontrado.");
        }

        List<StopInput> resolvidos = resolveStops(tenantId, stops);
        validar(categoria, dataExecucao, resolvidos);

        RoutePlan plan = new RoutePlan(
                tenantId, gestorPrincipal.userId(), driverId, vehicleId, categoria, dataExecucao, valor, viagemId);
        if (categoria == RouteCategoria.TRANSFER && vehicle != null) {
            calcularCustoEstimado(tenantId, vehicle, resolvidos)
                    .ifPresent(e -> plan.registrarCustoEstimado(
                            e.custoEstimado(), RouteCostEstimator.PRICING_FORMULA_VERSION));
        }
        routePlans.save(plan);
        for (int i = 0; i < resolvidos.size(); i++) {
            StopInput s = resolvidos.get(i);
            routeStops.save(new RouteStop(
                    plan.getId(), s.tipo(), s.label(), s.lat(), s.lon(), s.collectionPointId(),
                    s.janelaInicio(), s.janelaFim(), i, s.passengerId()));
        }
        registrarEvento(plan.getId(), RoutePlanEventType.CRIADA, gestorPrincipal.userId(), null);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public Page<RoutePlanResponse> listForGestor(JwtPrincipal gestorPrincipal, Pageable pageable) {
        Page<RoutePlan> plans = routePlans.findAllByTenantIdOrderByCreatedAtDesc(gestorPrincipal.tenantId(), pageable);
        return new org.springframework.data.domain.PageImpl<>(
                toResponses(plans.getContent()), pageable, plans.getTotalElements());
    }

    /** Atribuição direta pela tela de Rotas (spec 02) — nunca reatribui, nunca chamada com
     *  {@code forcar=true}. Ver {@link #assignDriver(JwtPrincipal, UUID, UUID, boolean, String)}. */
    @Transactional
    public RoutePlanResponse assignDriver(JwtPrincipal gestorPrincipal, UUID routePlanId, UUID driverId) {
        return assignDriver(gestorPrincipal, routePlanId, driverId, false, "tela_rotas");
    }

    /**
     * Idempotente quando já designada ao mesmo motorista. Se já designada a um motorista
     * diferente e {@code forcar=false}, lança {@link RoutePlanAlreadyAssignedException} —
     * nunca sobrescreve silenciosamente (achado da revisão do plano: reatribuição precisa
     * de erro explícito). {@code forcar=true} é só pra reatribuição de verdade, aprovada
     * pelo gestor via chat (ADR 0021) — nunca solto em outro caminho.
     *
     * <p>Lock pessimista ({@code findForUpdateById}) serializa atribuições concorrentes na
     * mesma rota: sem ele, duas chamadas quase simultâneas liam {@code driverId == null}
     * antes de qualquer uma commitar, e as duas passavam pelo guard — reatribuição
     * silenciosa que o comentário original já dizia ser impossível, mas não era, sob
     * concorrência (achado da revisão de código, 2026-08-25).
     */
    @Transactional
    public RoutePlanResponse assignDriver(
            JwtPrincipal gestorPrincipal, UUID routePlanId, UUID driverId, boolean forcar, String origem) {
        RoutePlan plan = Lookups.orNotFound(
                routePlans.findForUpdateById(routePlanId), "Rota não encontrada.");
        if (!plan.getTenantId().equals(gestorPrincipal.tenantId())) {
            throw new NotFoundException("Rota não encontrada.");
        }
        Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, gestorPrincipal.tenantId()), "Motorista não encontrado.");

        UUID driverAnterior = plan.getDriverId();
        if (driverAnterior != null && !driverAnterior.equals(driverId) && !forcar) {
            throw new RoutePlanAlreadyAssignedException();
        }
        if (!driverId.equals(driverAnterior)) {
            plan.designarMotorista(driverId);
            Map<String, Object> metadado = new LinkedHashMap<>();
            metadado.put("origem", origem);
            if (forcar && driverAnterior != null) {
                metadado.put("driverAnterior", driverAnterior.toString());
                registrarEvento(plan.getId(), RoutePlanEventType.REATRIBUIDA, gestorPrincipal.userId(), metadado);
            } else {
                registrarEvento(plan.getId(), RoutePlanEventType.ATRIBUIDA, gestorPrincipal.userId(), metadado);
                // Spec 14: passageiro avisado que a viagem está confirmada, assim que a
                // rota ganha motorista — fire-and-forget, nunca derruba a atribuição.
                if (passengerNotifications.notificarConfirmacao(plan)) {
                    plan.marcarPassageirosNotificados();
                }
            }
        }
        return toResponse(plan);
    }

    /** Cancelamento direto, pela tela de Rotas (ADR 0021) — só funciona pra PLANEJADA. Rota
     *  já EM_ANDAMENTO (alguma parada concluída) só cancela pelo chat, ver
     *  {@link #cancel(JwtPrincipal, UUID, boolean)}. */
    @Transactional
    public RoutePlanResponse cancel(JwtPrincipal gestorPrincipal, UUID routePlanId) {
        return cancel(gestorPrincipal, routePlanId, false);
    }

    /**
     * {@code viaChat=true} é o único caminho que cancela rota já {@code EM_ANDAMENTO}
     * (chamado por {@code ChatService}, nunca pelo controller de Rotas diretamente) — ADR
     * 0021: cancelar no meio do trâmite é uma decisão que fica registrada na conversa com o
     * motorista, não um botão isolado na lista. Paradas já concluídas não são desfeitas; só
     * a rota vira {@code CANCELADA}.
     */
    @Transactional
    public RoutePlanResponse cancel(JwtPrincipal gestorPrincipal, UUID routePlanId, boolean viaChat) {
        RoutePlan plan = Lookups.orNotFound(routePlans.findForUpdateById(routePlanId), "Rota não encontrada.");
        if (!plan.getTenantId().equals(gestorPrincipal.tenantId())) {
            throw new NotFoundException("Rota não encontrada.");
        }
        if (plan.getStatus() == RoutePlanStatus.CONCLUIDA || plan.getStatus() == RoutePlanStatus.CANCELADA) {
            throw new RoutePlanInvalidException("Rota " + plan.getStatus() + " não pode ser cancelada.");
        }
        if (plan.getStatus() == RoutePlanStatus.EM_ANDAMENTO && !viaChat) {
            throw new RoutePlanInvalidException(
                    "Rota já em andamento só pode ser cancelada pelo chat com o motorista.");
        }
        Map<String, Object> metadado = new LinkedHashMap<>();
        metadado.put("etapa", plan.getStatus().name());
        metadado.put("canal", viaChat ? "chat" : "tela");
        plan.avancarStatus(RoutePlanStatus.CANCELADA);
        registrarEvento(plan.getId(), RoutePlanEventType.CANCELADA, gestorPrincipal.userId(), metadado);
        // Spec 14: só avisa quem já tinha recebido a confirmação — não deixar "confirmado"
        // ser a última mensagem que o passageiro recebeu de uma viagem que não está mais de pé.
        passengerNotifications.notificarCancelamento(plan);
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
     *
     * <p>Busca o {@code RoutePlan} com lock pessimista antes de ler {@code todas} — sem
     * isso, duas conclusões de parada quase simultâneas (duplo toque, retry de rede) liam o
     * mesmo snapshot de paradas concluídas antes de qualquer uma commitar: as duas
     * calculavam a mesma {@code ordemReal} (duplicada) e nenhuma das duas via a rota como
     * 100% concluída, mesmo depois que as duas commitavam — a rota ficava presa em
     * EM_ANDAMENTO pra sempre (achado da revisão de código, 2026-08-25).
     */
    @Transactional
    public RouteStopResponse completeStop(JwtPrincipal driverPrincipal, UUID stopId) {
        Driver driver = driverResolver.resolve(driverPrincipal);
        RouteStop stop = Lookups.orNotFound(routeStops.findById(stopId), "Parada não encontrada.");
        RoutePlan plan = Lookups.orNotFound(routePlans.findForUpdateById(stop.getRoutePlanId()), "Parada não encontrada.");

        if (!driver.getId().equals(plan.getDriverId())) {
            throw new NotFoundException("Parada não encontrada.");
        }
        if (stop.isConcluida()) {
            return RouteStopResponse.from(stop);
        }

        List<RouteStop> todas = routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(plan.getId());
        int ordemReal = (int) todas.stream().filter(RouteStop::isConcluida).count() + 1;
        stop.concluir(ordemReal);

        Map<String, Object> metadadoParada = new LinkedHashMap<>();
        metadadoParada.put("ordemSugerida", stop.getOrdemSugerida());
        metadadoParada.put("ordemReal", ordemReal);
        metadadoParada.put("foraDeOrdem", ordemReal != stop.getOrdemSugerida() + 1);
        registrarEvento(plan.getId(), RoutePlanEventType.PARADA_CONCLUIDA, driverPrincipal.userId(), metadadoParada);

        if (plan.getStatus() == RoutePlanStatus.PLANEJADA) {
            plan.avancarStatus(RoutePlanStatus.EM_ANDAMENTO);
            // Spec 14: primeira parada concluída da rota = "motorista a caminho" pra todos
            // os passageiros da rota, não só o desta parada.
            passengerNotifications.notificarACaminho(plan);
        }
        // Spec 14: embarque confirmado é por parada — cada passageiro só sabe da própria.
        passengerNotifications.notificarEmbarqueConfirmado(plan, stop);
        boolean todasConcluidas = todas.stream().allMatch(s -> s.getId().equals(stop.getId()) || s.isConcluida());
        if (todasConcluidas) {
            plan.avancarStatus(RoutePlanStatus.CONCLUIDA);
            registrarEvento(plan.getId(), RoutePlanEventType.CONCLUIDA, driverPrincipal.userId(), null);
        }
        return RouteStopResponse.from(stop);
    }

    /** Botão "Avisar passageiro" (spec 14) — disparo manual, sob demanda, sem esperar o
     *  gatilho automático (ex. atraso, mudança de ponto de encontro combinada por telefone).
     *  Mesma checagem de posse de {@link #completeStop}: parada que não é do motorista
     *  devolve 404 genérico, nunca revela que a rota existe. */
    @Transactional(readOnly = true)
    public void notifyPassenger(JwtPrincipal driverPrincipal, UUID stopId) {
        Driver driver = driverResolver.resolve(driverPrincipal);
        RouteStop stop = Lookups.orNotFound(routeStops.findById(stopId), "Parada não encontrada.");
        RoutePlan plan = Lookups.orNotFound(routePlans.findById(stop.getRoutePlanId()), "Parada não encontrada.");
        if (!driver.getId().equals(plan.getDriverId())) {
            throw new NotFoundException("Parada não encontrada.");
        }
        passengerNotifications.notificarManualmente(plan, stop);
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

    /**
     * Versão em lote de {@link #toResponse} para telas de listagem — resolve nomes de
     * motorista/veículo e paradas com uma query cada (via {@code findAllById}/
     * {@code findAllByRoutePlanIdIn}) em vez de uma por plano, evitando N+1 em
     * {@link #listForGestor}.
     */
    private List<RoutePlanResponse> toResponses(List<RoutePlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> driverIds = plans.stream().map(RoutePlan::getDriverId).filter(java.util.Objects::nonNull).toList();
        List<UUID> vehicleIds = plans.stream().map(RoutePlan::getVehicleId).filter(java.util.Objects::nonNull).toList();
        List<UUID> planIds = plans.stream().map(RoutePlan::getId).toList();

        java.util.Map<UUID, String> driverNames = drivers.findAllById(driverIds).stream()
                .collect(java.util.stream.Collectors.toMap(Driver::getId, Driver::getName));
        java.util.Map<UUID, String> vehiclePlates = vehicles.findAllById(vehicleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Vehicle::getId, Vehicle::getPlate));
        java.util.Map<UUID, List<RouteStopResponse>> stopsByPlan =
                routeStops.findAllByRoutePlanIdInOrderByOrdemSugeridaAsc(planIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                RouteStop::getRoutePlanId,
                                java.util.stream.Collectors.mapping(RouteStopResponse::from, java.util.stream.Collectors.toList())));

        return plans.stream()
                .map(plan -> RoutePlanResponse.from(
                        plan,
                        driverNames.get(plan.getDriverId()),
                        vehiclePlates.get(plan.getVehicleId()),
                        stopsByPlan.getOrDefault(plan.getId(), List.of())))
                .toList();
    }
}
