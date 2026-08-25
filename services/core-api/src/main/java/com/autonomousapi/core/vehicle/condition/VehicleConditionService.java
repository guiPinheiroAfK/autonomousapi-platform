package com.autonomousapi.core.vehicle.condition;

import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.condition.dto.VehicleConditionScoreResponse;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentRequest;
import com.autonomousapi.core.vehicle.condition.dto.VehicleIncidentResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinistro e condição do veículo (spec 06, item 2). Fórmula do score v1, deliberadamente
 * simples e versionada (spec pede exatamente isso — "tratar a fórmula inicial como
 * heurística simples", calibrar depois com dado real): 100 menos uma penalidade por
 * sinistro, por severidade, com piso em zero.
 */
@Service
public class VehicleConditionService {

    private static final String ALGORITHM_VERSION = "v1-incident-penalty";

    private static final Map<IncidentSeverity, Integer> PENALIDADE = Map.of(
            IncidentSeverity.LEVE, 5,
            IncidentSeverity.MODERADA, 15,
            IncidentSeverity.GRAVE, 30);

    private final VehicleRepository vehicles;
    private final VehicleIncidentRepository incidents;
    private final VehicleConditionScoreRepository scores;

    public VehicleConditionService(
            VehicleRepository vehicles, VehicleIncidentRepository incidents, VehicleConditionScoreRepository scores) {
        this.vehicles = vehicles;
        this.incidents = incidents;
        this.scores = scores;
    }

    @Transactional
    public VehicleIncidentResponse registerIncident(
            JwtPrincipal principal, UUID vehicleId, VehicleIncidentRequest req) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        VehicleIncident incident = new VehicleIncident(
                vehicle.getId(), req.data(), req.severidade(), req.descricao(), req.custoReparo());
        incidents.save(incident);
        recomputarScore(vehicle.getId());
        return VehicleIncidentResponse.from(incident);
    }

    @Transactional(readOnly = true)
    public List<VehicleIncidentResponse> listIncidents(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        return incidents.findAllByVehicleIdOrderByDataDesc(vehicle.getId()).stream()
                .map(VehicleIncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleConditionScoreResponse score(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        return scores.findByVehicleId(vehicle.getId())
                .map(s -> new VehicleConditionScoreResponse(vehicle.getId(), s.getScore(), s.getAlgorithmVersion()))
                .orElseGet(() -> VehicleConditionScoreResponse.cheio(vehicle.getId()));
    }

    private void recomputarScore(UUID vehicleId) {
        int penalidadeTotal = incidents.findAllByVehicleIdOrderByDataDesc(vehicleId).stream()
                .mapToInt(i -> PENALIDADE.get(i.getSeveridade()))
                .sum();
        BigDecimal score = BigDecimal.valueOf(Math.max(0, 100 - penalidadeTotal));

        VehicleConditionScore existente = scores.findByVehicleId(vehicleId)
                .orElseGet(() -> new VehicleConditionScore(vehicleId, score, ALGORITHM_VERSION));
        existente.atualizar(score, ALGORITHM_VERSION);
        scores.save(existente);
    }

    private Vehicle findOwnedVehicle(JwtPrincipal principal, UUID vehicleId) {
        return Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, principal.tenantId()), "Veículo não encontrado.");
    }
}
