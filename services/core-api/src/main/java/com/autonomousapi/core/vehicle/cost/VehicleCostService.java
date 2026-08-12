package com.autonomousapi.core.vehicle.cost;

import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.cost.dto.MonthlyCostResponse;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryRequest;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostEntryResponse;
import com.autonomousapi.core.vehicle.cost.dto.VehicleCostSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /** Gráfico de tendência do dashboard mostra os últimos 6 meses (mês corrente incluso). */
    private static final int TREND_MONTHS = 6;

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

    /** Soma de custos por mês em toda a frota do tenant, últimos {@value #TREND_MONTHS} meses. */
    @Transactional(readOnly = true)
    public List<MonthlyCostResponse> monthlyTrend(JwtPrincipal principal) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(TREND_MONTHS - 1L);
        LocalDate since = firstMonth.atDay(1);

        Map<YearMonth, BigDecimal> totalsByMonth = costs.findAllByTenantIdSince(principal.tenantId(), since)
                .stream()
                .collect(Collectors.groupingBy(
                        c -> YearMonth.from(c.getOccurredAt()),
                        Collectors.reducing(BigDecimal.ZERO, VehicleCostEntry::getAmount, BigDecimal::add)));

        List<MonthlyCostResponse> trend = new ArrayList<>();
        for (int i = 0; i < TREND_MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            trend.add(new MonthlyCostResponse(month.toString(), totalsByMonth.getOrDefault(month, BigDecimal.ZERO)));
        }
        return trend;
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
