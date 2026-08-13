package com.autonomousapi.core.driver.rating.dto;

import com.autonomousapi.core.driver.rating.DriverRatingManual;
import java.time.Instant;
import java.util.UUID;

public record DriverRatingResponse(
        UUID id, UUID driverId, UUID gestorUserId, short nota, String comentario, Instant createdAt) {

    public static DriverRatingResponse from(DriverRatingManual r) {
        return new DriverRatingResponse(
                r.getId(), r.getDriverId(), r.getGestorUserId(), r.getNota(), r.getComentario(), r.getCreatedAt());
    }
}
