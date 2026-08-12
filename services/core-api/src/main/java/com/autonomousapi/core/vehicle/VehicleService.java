package com.autonomousapi.core.vehicle;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.PlateAlreadyUsedException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.dto.VehicleMaintenanceAlertResponse;
import com.autonomousapi.core.vehicle.dto.VehicleRequest;
import com.autonomousapi.core.vehicle.dto.VehicleResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo acesso é escopado ao tenant do usuário autenticado (spec 01): as queries do
 * repositório já filtram por tenantId, então um gestor nunca lê/edita veículo de
 * outro tenant — nem por bug de UI, nem por chamada direta à API (404, não 403,
 * para não revelar a existência do recurso em outro tenant).
 */
@Service
public class VehicleService {

    /** Alerta dispara com manutenção a até 15 dias OU 1000km de distância (spec 05, Fase 1). */
    private static final int MAINTENANCE_DAYS_THRESHOLD = 15;
    private static final int MAINTENANCE_KM_THRESHOLD = 1000;

    private final VehicleRepository vehicles;

    public VehicleService(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    @Transactional
    public VehicleResponse create(JwtPrincipal principal, VehicleRequest req) {
        UUID tenantId = principal.tenantId();
        if (vehicles.existsByTenantIdAndPlateIgnoreCase(tenantId, req.plate())) {
            throw new PlateAlreadyUsedException();
        }
        Vehicle vehicle = new Vehicle(
                tenantId, req.plate(), req.brand(), req.model(), req.modelYear(), req.odometerKm());
        vehicle.update(req.plate(), req.brand(), req.model(), req.modelYear(), req.odometerKm(),
                req.status(), req.proximaManutencaoData(), req.proximaManutencaoKm(), req.atributos());
        vehicles.save(vehicle);
        return VehicleResponse.from(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(JwtPrincipal principal) {
        return vehicles.findAllByTenantIdOrderByCreatedAtDesc(principal.tenantId()).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(JwtPrincipal principal, UUID id) {
        return VehicleResponse.from(findOwned(principal, id));
    }

    @Transactional
    public VehicleResponse update(JwtPrincipal principal, UUID id, VehicleRequest req) {
        Vehicle vehicle = findOwned(principal, id);
        if (vehicles.existsByTenantIdAndPlateIgnoreCaseAndIdNot(principal.tenantId(), req.plate(), id)) {
            throw new PlateAlreadyUsedException();
        }
        vehicle.update(req.plate(), req.brand(), req.model(), req.modelYear(),
                req.odometerKm(), req.status(), req.proximaManutencaoData(), req.proximaManutencaoKm(),
                req.atributos());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID id) {
        Vehicle vehicle = findOwned(principal, id);
        vehicles.delete(vehicle);
    }

    /** Veículos com manutenção vencida ou a vencer nos próximos 15 dias / 1000km. */
    @Transactional(readOnly = true)
    public List<VehicleMaintenanceAlertResponse> maintenanceDue(JwtPrincipal principal) {
        LocalDate today = LocalDate.now();
        return vehicles.findAllByTenantIdWithManutencaoAgendada(principal.tenantId()).stream()
                .map(v -> toAlert(v, today))
                .filter(a -> (a.diasRestantes() != null && a.diasRestantes() <= MAINTENANCE_DAYS_THRESHOLD)
                        || (a.kmRestante() != null && a.kmRestante() <= MAINTENANCE_KM_THRESHOLD))
                .sorted(Comparator.comparing(
                        a -> Math.min(
                                a.diasRestantes() != null ? a.diasRestantes() : Long.MAX_VALUE,
                                a.kmRestante() != null ? a.kmRestante() : Long.MAX_VALUE)))
                .toList();
    }

    private VehicleMaintenanceAlertResponse toAlert(Vehicle v, LocalDate today) {
        Long diasRestantes = v.getProximaManutencaoData() != null
                ? ChronoUnit.DAYS.between(today, v.getProximaManutencaoData())
                : null;
        Integer kmRestante = v.getProximaManutencaoKm() != null
                ? v.getProximaManutencaoKm() - v.getOdometerKm()
                : null;
        return new VehicleMaintenanceAlertResponse(
                v.getId(), v.getPlate(), v.getBrand(), v.getModel(),
                v.getProximaManutencaoData(), diasRestantes, v.getProximaManutencaoKm(), kmRestante);
    }

    private Vehicle findOwned(JwtPrincipal principal, UUID id) {
        return vehicles.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }
}
