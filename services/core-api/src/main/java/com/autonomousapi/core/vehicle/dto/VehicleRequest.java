package com.autonomousapi.core.vehicle.dto;

import com.autonomousapi.core.vehicle.VehicleStatus;
import com.autonomousapi.core.vehicle.VehicleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record VehicleRequest(
        @NotBlank @Size(max = 10) String plate,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 80) String model,
        Integer modelYear,
        @NotNull @Min(0) Integer odometerKm,
        @NotNull VehicleStatus status,
        /** Opcional (spec 08 item 4) — sem valor, a listagem usa o ícone genérico. */
        VehicleType tipo,
        /** Opcional — sem valor, o veículo não entra no alerta de manutenção. */
        LocalDate proximaManutencaoData,
        Integer proximaManutencaoKm,
        /**
         * Atributos que variam por tipo de veículo (ADR 0008): autonomia e conector num
         * elétrico, cilindrada numa moto, valor FIPE. Nulo é tratado como mapa vazio.
         */
        Map<String, Object> atributos) {
}
