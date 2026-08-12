package com.autonomousapi.core.trip.dto;

import com.autonomousapi.core.trip.Trip;
import java.time.Instant;
import java.util.UUID;

public record TripResponse(
        UUID id, UUID vehicleId, String status, Instant startedAt, Instant endedAt) {

    public static TripResponse from(Trip t) {
        return new TripResponse(
                t.getId(), t.getVehicleId(), t.getStatus().name(), t.getStartedAt(), t.getEndedAt());
    }
}
