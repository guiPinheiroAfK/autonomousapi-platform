package com.autonomousapi.core.vehicle;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.PlateAlreadyUsedException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.dto.VehicleRequest;
import com.autonomousapi.core.vehicle.dto.VehicleResponse;
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
                req.odometerKm(), req.status());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID id) {
        Vehicle vehicle = findOwned(principal, id);
        vehicles.delete(vehicle);
    }

    private Vehicle findOwned(JwtPrincipal principal, UUID id) {
        return vehicles.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }
}
