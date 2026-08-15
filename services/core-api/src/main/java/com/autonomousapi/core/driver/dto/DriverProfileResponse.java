package com.autonomousapi.core.driver.dto;

import com.autonomousapi.core.driver.Driver;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Perfil do próprio motorista (spec 07, item 1-2). Deliberadamente enxuto: nunca inclui
 * avaliação (driver_rating não é campo do Driver — nem existe o que vazar aqui, spec 06/07).
 */
public record DriverProfileResponse(UUID id, String name, String cnh, LocalDate cnhValidade, String phone) {

    public static DriverProfileResponse from(Driver d) {
        return new DriverProfileResponse(d.getId(), d.getName(), d.getCnh(), d.getCnhValidade(), d.getPhone());
    }
}
