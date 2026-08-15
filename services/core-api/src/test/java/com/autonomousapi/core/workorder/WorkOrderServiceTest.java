package com.autonomousapi.core.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.workorder.dto.WorkOrderItemRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderRequest;
import com.autonomousapi.core.workorder.dto.WorkOrderResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkOrderServiceTest {

    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final WorkOrderItemRepository items = mock(WorkOrderItemRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenantId, "GESTOR_FROTA");

    private WorkOrderService service() {
        return new WorkOrderService(workOrders, items, vehicles, drivers);
    }

    private WorkOrderRequest request() {
        return new WorkOrderRequest(
                vehicleId, null, WorkOrderType.CORRETIVA, WorkOrderStatus.ABERTA, WorkOrderPriority.ALTA,
                "Problema no freio", null, "Oficina Central", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10), 1000,
                List.of(new WorkOrderItemRequest("Pastilha de freio", 2, new BigDecimal("120.00"))));
    }

    @Test
    void createGeraNumeroComAnoEContagemDoTenant() {
        Vehicle vehicle = new Vehicle(tenantId, "ABC1D23", "Fiat", "Fiorino", 2022, 1000);
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(workOrders.countByTenantIdAndDataAberturaBetween(eq(tenantId), any(), any())).thenReturn(7L);
        when(items.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkOrderResponse response = service().create(principal, request());

        assertEquals("OS-2026-0008", response.numero());
        assertEquals(new BigDecimal("240.00"), response.custoTotal());
        verify(workOrders).save(any());
    }

    @Test
    void createComVeiculoDeOutroTenantLancaNotFound() {
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().create(principal, request()));
    }

    @Test
    void updateSubstituiItensAntigos() {
        UUID orderId = UUID.randomUUID();
        WorkOrder existente = new WorkOrder(tenantId, vehicleId, "OS-2026-0001");
        Vehicle vehicle = new Vehicle(tenantId, "ABC1D23", "Fiat", "Fiorino", 2022, 1000);

        when(workOrders.findByIdAndTenantId(orderId, tenantId)).thenReturn(Optional.of(existente));
        when(vehicles.findByIdAndTenantId(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));
        when(items.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service().update(principal, orderId, request());

        verify(items).deleteAllByWorkOrderId(existente.getId());
        verify(items).saveAll(any());
    }

    @Test
    void deleteRemoveItensAntesDaOrdem() {
        UUID orderId = UUID.randomUUID();
        WorkOrder existente = new WorkOrder(tenantId, vehicleId, "OS-2026-0001");
        when(workOrders.findByIdAndTenantId(orderId, tenantId)).thenReturn(Optional.of(existente));

        service().delete(principal, orderId);

        verify(items).deleteAllByWorkOrderId(existente.getId());
        verify(workOrders).delete(existente);
    }
}
