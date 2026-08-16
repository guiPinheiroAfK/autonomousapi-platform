package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatConversation;
import java.time.Instant;
import java.util.UUID;

public record ChatConversationResponse(
        UUID id, UUID driverId, String driverName, UUID vehicleId, String vehiclePlate, Instant createdAt) {

    public static ChatConversationResponse from(ChatConversation c, String driverName, String vehiclePlate) {
        return new ChatConversationResponse(
                c.getId(), c.getDriverId(), driverName, c.getVehicleId(), vehiclePlate, c.getCreatedAt());
    }
}
