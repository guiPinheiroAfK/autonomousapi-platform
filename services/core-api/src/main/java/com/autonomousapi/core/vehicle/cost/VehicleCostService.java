package com.autonomousapi.core.vehicle.cost;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryRequest;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryResponse;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custo por km (spec 05, Fase 1): totalCost / odometerKm atual do veículo. Cálculo
 * simples de MVP — não rastreia km "no momento do lançamento", usa o odômetro
 * corrente do veículo como km total rodado desde o cadastro.
 *
 * Todo acesso resolve o veículo via VehicleRepository#findByIdAndTenantId primeiro:
 * reaproveita o mesmo escopo por tenant do VehicleService, sem duplicar a checagem.
 */
@Service
public class VehicleCostService {

    private final VehicleRepository vehicles;
    private final VehicleCostEntryRepository costs;

    public VehicleCostService(VehicleRepository vehicles, VehicleCostEntryRepository costs) {
        this.vehicles = vehicles;
        this.costs = costs;
    }

    @Transactional
    public VehicleCostEntryResponse addEntry(
            JwtPrincipal principal, UUID vehicleId, VehicleCostEntryRequest req) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        VehicleCostEntry entry = new VehicleCostEntry(
                vehicle.getId(), req.category(), req.amount(), req.description(), req.occurredAt());
        costs.save(entry);
        return VehicleCostEntryResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<VehicleCostEntryResponse> list(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        return costs.findAllByVehicleIdOrderByOccurredAtDesc(vehicle.getId()).stream()
                .map(VehicleCostEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleCostSummaryResponse summary(JwtPrincipal principal, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        BigDecimal total = costs.sumAmountByVehicleId(vehicle.getId());
        int odometerKm = vehicle.getOdometerKm();
        BigDecimal costPerKm = odometerKm == 0
                ? null
                : total.divide(BigDecimal.valueOf(odometerKm), 2, RoundingMode.HALF_UP);
        return new VehicleCostSummaryResponse(vehicle.getId(), total, odometerKm, costPerKm);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID vehicleId, UUID costId) {
        Vehicle vehicle = findOwnedVehicle(principal, vehicleId);
        VehicleCostEntry entry = costs.findByIdAndVehicleId(costId, vehicle.getId())
                .orElseThrow(() -> new NotFoundException("Lançamento de custo não encontrado."));
        costs.delete(entry);
    }

    private Vehicle findOwnedVehicle(JwtPrincipal principal, UUID vehicleId) {
        return vehicles.findByIdAndTenantId(vehicleId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }
}
