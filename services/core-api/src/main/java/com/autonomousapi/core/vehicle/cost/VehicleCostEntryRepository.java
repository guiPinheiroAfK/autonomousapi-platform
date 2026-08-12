package com.autonomousapi.core.vehicle.cost;

import com.autonomousapi.core.vehicle.cost.dto.FleetCostEntryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleCostEntryRepository extends JpaRepository<VehicleCostEntry, UUID> {

    List<VehicleCostEntry> findAllByVehicleIdOrderByOccurredAtDesc(UUID vehicleId);

    Optional<VehicleCostEntry> findByIdAndVehicleId(UUID id, UUID vehicleId);

    @Query("select coalesce(sum(c.amount), 0) from VehicleCostEntry c where c.vehicleId = :vehicleId")
    BigDecimal sumAmountByVehicleId(@Param("vehicleId") UUID vehicleId);

    /** Custos de toda a frota do tenant desde {@code since} — usado no gráfico de tendência do dashboard. */
    @Query("select c from VehicleCostEntry c, com.autonomousapi.core.vehicle.Vehicle v "
            + "where v.id = c.vehicleId and v.tenantId = :tenantId and c.occurredAt >= :since "
            + "order by c.occurredAt")
    List<VehicleCostEntry> findAllByTenantIdSince(
            @Param("tenantId") UUID tenantId, @Param("since") LocalDate since);

    /**
     * Custos da frota inteira já com os dados do veículo, em UMA query — o front antes
     * resolvia isso com 1+N requisições (lista de veículos, depois custos de cada um).
     */
    @Query("select new com.autonomousapi.core.vehicle.cost.dto.FleetCostEntryResponse("
            + "c.id, v.id, v.plate, v.brand, v.model, c.category, c.amount, c.description, c.occurredAt) "
            + "from VehicleCostEntry c, com.autonomousapi.core.vehicle.Vehicle v "
            + "where v.id = c.vehicleId and v.tenantId = :tenantId "
            + "order by c.occurredAt desc")
    List<FleetCostEntryResponse> findFleetCosts(@Param("tenantId") UUID tenantId);

    /** Idem, filtrando categoria (ex.: só MANUTENCAO, para a tela de Manutenção). */
    @Query("select new com.autonomousapi.core.vehicle.cost.dto.FleetCostEntryResponse("
            + "c.id, v.id, v.plate, v.brand, v.model, c.category, c.amount, c.description, c.occurredAt) "
            + "from VehicleCostEntry c, com.autonomousapi.core.vehicle.Vehicle v "
            + "where v.id = c.vehicleId and v.tenantId = :tenantId and c.category = :category "
            + "order by c.occurredAt desc")
    List<FleetCostEntryResponse> findFleetCostsByCategory(
            @Param("tenantId") UUID tenantId, @Param("category") VehicleCostCategory category);
}
