package com.autonomousapi.core.me;

import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverAssignmentService;
import com.autonomousapi.core.driver.dto.DriverAssignmentResponse;
import com.autonomousapi.core.driver.dto.DriverProfileResponse;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.trip.TripService;
import com.autonomousapi.core.trip.dto.TripResponse;
import com.autonomousapi.core.vehicle.condition.VehicleConditionService;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentRequest;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentResponse;
import com.autonomousapi.core.workorder.WorkOrderService;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Superfície read-only do app do motorista (spec 07, ADR 0013): compõe os services já
 * existentes (Trip, WorkOrder, VehicleCondition), sem duplicar lógica — só resolve
 * "quem é o motorista" e "qual é o veículo dele" a partir do token, nunca do cliente.
 */
@Service
public class MeService {

    private final CurrentDriverResolver driverResolver;
    private final DriverAssignmentService assignmentService;
    private final WorkOrderService workOrderService;
    private final TripService tripService;
    private final VehicleConditionService conditionService;

    public MeService(
            CurrentDriverResolver driverResolver,
            DriverAssignmentService assignmentService,
            WorkOrderService workOrderService,
            TripService tripService,
            VehicleConditionService conditionService) {
        this.driverResolver = driverResolver;
        this.assignmentService = assignmentService;
        this.workOrderService = workOrderService;
        this.tripService = tripService;
        this.conditionService = conditionService;
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse profile(JwtPrincipal principal) {
        return DriverProfileResponse.from(driverResolver.resolve(principal));
    }

    /** Designação ativa do motorista, null se não houver nenhuma no momento. */
    @Transactional(readOnly = true)
    public DriverAssignmentResponse vehicle(JwtPrincipal principal) {
        Driver driver = driverResolver.resolve(principal);
        return assignmentService.activeForDriver(principal, driver.getId());
    }

    /** OS do veículo atual do motorista, read-only. Lista vazia se não houver veículo designado.
     *  Contrato deste endpoint continua lista simples; por baixo, limitado às 50 mais
     *  recentes (histórico de OS de um único veículo é naturalmente pequeno). */
    @Transactional(readOnly = true)
    public List<WorkOrderResponse> vehicleWorkOrders(JwtPrincipal principal) {
        DriverAssignmentResponse active = vehicle(principal);
        if (active == null) {
            return List.of();
        }
        return workOrderService.list(principal, active.vehicleId(), PageRequest.of(0, 50)).getContent();
    }

    /**
     * Viagens do próprio motorista — já filtradas por userId dentro do TripService.
     * Contrato deste endpoint continua uma lista simples (a tela só mostra as 10 mais
     * recentes, {@code DriverMorePage.tsx}); por baixo, a query agora é limitada às 20
     * mais recentes em vez de trazer o histórico inteiro (cleanup de performance).
     */
    @Transactional(readOnly = true)
    public List<TripResponse> trips(JwtPrincipal principal) {
        return tripService.list(principal, PageRequest.of(0, 20)).getContent();
    }

    /**
     * Reporte de ocorrência do veículo designado (spec 07, item 4; reaproveita o endpoint
     * de sinistro do spec 06 — RF016). Exige veículo designado: o motorista nunca informa
     * vehicleId, ele vem da designação ativa resolvida no servidor.
     */
    @Transactional
    public VehicleIncidentResponse reportIncident(JwtPrincipal principal, VehicleIncidentRequest req) {
        DriverAssignmentResponse active = vehicle(principal);
        if (active == null) {
            throw new NotFoundException("Você não tem veículo designado no momento.");
        }
        return conditionService.registerIncident(principal, active.vehicleId(), req);
    }
}
