package com.autonomousapi.core.workorder;

import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.workorder.dto.MonthlyMaintenanceCostResponse;
import com.autonomousapi.core.workorder.dto.VehicleMaintenanceCostResponse;
import com.autonomousapi.core.workorder.dto.WorkOrderItemRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderReportResponse;
import com.autonomousapi.core.workorder.dto.WorkOrderRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ordens de serviço (manutenção/oficina). Mesmo escopo por tenant do resto do app. */
@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrders;
    private final WorkOrderItemRepository items;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;

    public WorkOrderService(
            WorkOrderRepository workOrders,
            WorkOrderItemRepository items,
            VehicleRepository vehicles,
            DriverRepository drivers) {
        this.workOrders = workOrders;
        this.items = items;
        this.vehicles = vehicles;
        this.drivers = drivers;
    }

    @Transactional
    public WorkOrderResponse create(JwtPrincipal principal, WorkOrderRequest req) {
        UUID tenantId = principal.tenantId();
        Vehicle vehicle = findOwnedVehicle(tenantId, req.vehicleId());
        Driver driver = req.driverId() != null ? findOwnedDriver(tenantId, req.driverId()) : null;

        WorkOrder wo = new WorkOrder(tenantId, req.vehicleId(), gerarNumero(tenantId, req.dataAbertura()));
        applyRequest(wo, req);
        workOrders.save(wo);

        List<WorkOrderItem> savedItems = saveItems(wo.getId(), req.itens());
        return WorkOrderResponse.from(wo, savedItems, vehicle, driver);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(JwtPrincipal principal, UUID vehicleId, Pageable pageable) {
        UUID tenantId = principal.tenantId();
        Page<WorkOrder> orders = vehicleId != null
                ? workOrders.findAllByTenantIdAndVehicleIdOrderByDataAberturaDesc(tenantId, vehicleId, pageable)
                : workOrders.findAllByTenantIdOrderByDataAberturaDesc(tenantId, pageable);

        // Só os veículos/motoristas realmente referenciados pelas OS retornadas — antes
        // carregava a frota/equipe inteira do tenant (achado da auditoria de cleanup).
        List<UUID> vehicleIds = orders.stream().map(WorkOrder::getVehicleId).distinct().toList();
        List<UUID> driverIds = orders.stream().map(WorkOrder::getDriverId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Vehicle> vehicleById = vehicles.findAllById(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        Map<UUID, Driver> driverById = drivers.findAllById(driverIds).stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));

        List<UUID> orderIds = orders.stream().map(WorkOrder::getId).toList();
        Map<UUID, List<WorkOrderItem>> itemsByOrder = items.findAllByWorkOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(WorkOrderItem::getWorkOrderId));

        return orders.map(wo -> WorkOrderResponse.from(
                wo,
                itemsByOrder.getOrDefault(wo.getId(), List.of()),
                vehicleById.get(wo.getVehicleId()),
                wo.getDriverId() != null ? driverById.get(wo.getDriverId()) : null));
    }

    @Transactional
    public WorkOrderResponse update(JwtPrincipal principal, UUID id, WorkOrderRequest req) {
        UUID tenantId = principal.tenantId();
        WorkOrder wo = findOwned(tenantId, id);
        Vehicle vehicle = findOwnedVehicle(tenantId, req.vehicleId());
        Driver driver = req.driverId() != null ? findOwnedDriver(tenantId, req.driverId()) : null;

        applyRequest(wo, req);

        items.deleteAllByWorkOrderId(wo.getId());
        List<WorkOrderItem> savedItems = saveItems(wo.getId(), req.itens());

        return WorkOrderResponse.from(wo, savedItems, vehicle, driver);
    }

    @Transactional
    public void delete(JwtPrincipal principal, UUID id) {
        WorkOrder wo = findOwned(principal.tenantId(), id);
        items.deleteAllByWorkOrderId(wo.getId());
        workOrders.delete(wo);
    }

    private void applyRequest(WorkOrder wo, WorkOrderRequest req) {
        wo.update(
                req.vehicleId(), req.driverId(), req.tipo(), req.status(), req.prioridade(),
                req.descricaoProblema(), req.observacoes(), req.responsavelOficina(),
                req.dataAbertura(), req.previsaoConclusao(), req.kmAbertura());
    }

    private List<WorkOrderItem> saveItems(UUID workOrderId, List<WorkOrderItemRequest> itemRequests) {
        List<WorkOrderItem> entities = itemRequests.stream()
                .map(i -> new WorkOrderItem(workOrderId, i.descricao(), i.quantidade(), i.valorUnitario()))
                .toList();
        return items.saveAll(entities);
    }

    /** "OS-2026-0001": ano da data de abertura + contagem de OS do tenant naquele ano + 1. */
    private String gerarNumero(UUID tenantId, LocalDate dataAbertura) {
        int ano = dataAbertura.getYear();
        long contagem = workOrders.countByTenantIdAndDataAberturaBetween(
                tenantId, Year.of(ano).atDay(1), Year.of(ano).atMonth(12).atEndOfMonth());
        return "OS-%d-%04d".formatted(ano, contagem + 1);
    }

    private WorkOrder findOwned(UUID tenantId, UUID id) {
        return Lookups.orNotFound(workOrders.findByIdAndTenantId(id, tenantId), "Ordem de serviço não encontrada.");
    }

    private Vehicle findOwnedVehicle(UUID tenantId, UUID vehicleId) {
        return Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, tenantId), "Veículo não encontrado.");
    }

    private Driver findOwnedDriver(UUID tenantId, UUID driverId) {
        return Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, tenantId), "Motorista não encontrado.");
    }

    /** Últimos 12 meses fechados (spec 05, tela de Relatórios): custo por tipo + ranking de veículo. */
    private static final int REPORT_MONTHS = 12;

    @Transactional(readOnly = true)
    public WorkOrderReportResponse maintenanceSummary(JwtPrincipal principal) {
        UUID tenantId = principal.tenantId();
        List<WorkOrder> orders = workOrders.findAllByTenantIdOrderByDataAberturaDesc(tenantId);
        List<UUID> orderIds = orders.stream().map(WorkOrder::getId).toList();
        Map<UUID, List<WorkOrderItem>> itemsByOrder = items.findAllByWorkOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(WorkOrderItem::getWorkOrderId));

        // Só os veículos com OS no período, não a frota inteira do tenant.
        Map<UUID, Vehicle> vehicleById = vehicles.findAllById(orders.stream().map(WorkOrder::getVehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        return new WorkOrderReportResponse(
                monthlyBreakdown(orders, itemsByOrder), vehicleRanking(orders, itemsByOrder, vehicleById));
    }

    private List<MonthlyMaintenanceCostResponse> monthlyBreakdown(
            List<WorkOrder> orders, Map<UUID, List<WorkOrderItem>> itemsByOrder) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(REPORT_MONTHS - 1L);

        Map<YearMonth, List<WorkOrder>> byMonth = orders.stream()
                .filter(wo -> !YearMonth.from(wo.getDataAbertura()).isBefore(firstMonth))
                .collect(Collectors.groupingBy(wo -> YearMonth.from(wo.getDataAbertura())));

        List<MonthlyMaintenanceCostResponse> result = new ArrayList<>();
        for (int i = 0; i < REPORT_MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            List<WorkOrder> monthOrders = byMonth.getOrDefault(month, List.of());
            result.add(new MonthlyMaintenanceCostResponse(
                    month.toString(),
                    custoPorTipo(monthOrders, itemsByOrder, WorkOrderType.PREVENTIVA),
                    custoPorTipo(monthOrders, itemsByOrder, WorkOrderType.CORRETIVA),
                    custoPorTipo(monthOrders, itemsByOrder, WorkOrderType.REVISAO),
                    custoPorTipo(monthOrders, itemsByOrder, WorkOrderType.SINISTRO),
                    monthOrders.size()));
        }
        return result;
    }

    private BigDecimal custoPorTipo(
            List<WorkOrder> orders, Map<UUID, List<WorkOrderItem>> itemsByOrder, WorkOrderType tipo) {
        return orders.stream()
                .filter(wo -> wo.getTipo() == tipo)
                .flatMap(wo -> itemsByOrder.getOrDefault(wo.getId(), List.of()).stream())
                .map(WorkOrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Ranking é histórico completo (todo o período), não só os últimos 12 meses — mesmo escopo do mock anterior. */
    private List<VehicleMaintenanceCostResponse> vehicleRanking(
            List<WorkOrder> orders, Map<UUID, List<WorkOrderItem>> itemsByOrder, Map<UUID, Vehicle> vehicleById) {
        Map<UUID, BigDecimal> totalByVehicle = new java.util.HashMap<>();
        for (WorkOrder wo : orders) {
            BigDecimal custo = itemsByOrder.getOrDefault(wo.getId(), List.of()).stream()
                    .map(WorkOrderItem::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalByVehicle.merge(wo.getVehicleId(), custo, BigDecimal::add);
        }

        return totalByVehicle.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Comparator.<Map.Entry<UUID, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue).reversed())
                .limit(6)
                .map(e -> {
                    Vehicle v = vehicleById.get(e.getKey());
                    return new VehicleMaintenanceCostResponse(
                            e.getKey(), v != null ? v.getPlate() : null,
                            v != null ? v.getBrand() + " " + v.getModel() : null, e.getValue());
                })
                .toList();
    }
}
