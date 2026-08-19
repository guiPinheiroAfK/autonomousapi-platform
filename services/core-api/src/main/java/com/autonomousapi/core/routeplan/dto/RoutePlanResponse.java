package com.autonomousapi.core.routeplan.dto;

import com.autonomousapi.core.routeplan.RouteCategoria;
import com.autonomousapi.core.routeplan.RoutePlan;
import com.autonomousapi.core.routeplan.RoutePlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoutePlanResponse(
        UUID id,
        UUID driverId,
        String driverName,
        UUID vehicleId,
        String vehiclePlate,
        RoutePlanStatus status,
        RouteCategoria categoria,
        LocalDate dataExecucao,
        BigDecimal valor,
        BigDecimal custoEstimado,
        /** {@code valor - custoEstimado}, derivado na leitura (spec 10) — só quando a rota
         *  está CONCLUIDA e os dois valores existem; null em qualquer outro caso (não é uma
         *  margem "prevista", é a margem de uma rota que de fato aconteceu). */
        BigDecimal margemRealizada,
        Instant createdAt,
        List<RouteStopResponse> stops) {

    public static RoutePlanResponse from(
            RoutePlan p, String driverName, String vehiclePlate, List<RouteStopResponse> stops) {
        BigDecimal margemRealizada = p.getStatus() == RoutePlanStatus.CONCLUIDA
                        && p.getValor() != null && p.getCustoEstimado() != null
                ? p.getValor().subtract(p.getCustoEstimado())
                : null;
        return new RoutePlanResponse(
                p.getId(), p.getDriverId(), driverName, p.getVehicleId(), vehiclePlate, p.getStatus(),
                p.getCategoria(), p.getDataExecucao(), p.getValor(), p.getCustoEstimado(), margemRealizada,
                p.getCreatedAt(), stops);
    }
}
