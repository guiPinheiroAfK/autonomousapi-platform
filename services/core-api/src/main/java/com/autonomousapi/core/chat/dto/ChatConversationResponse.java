package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatConversation;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code tenantName} — não "gestorName": {@code app_user} não tem coluna de nome de pessoa
 * (só {@code email}), então o que o motorista vê do outro lado é o nome da frota/empresa
 * ({@code tenant.name}), nunca um nome próprio. Nomear certo evita reabrir essa dúvida
 * depois (mesma armadilha do "OS de manutenção" vs. "OS de rota" no spec 07).
 */
public record ChatConversationResponse(
        UUID id,
        UUID driverId,
        String driverName,
        String tenantName,
        UUID vehicleId,
        String vehiclePlate,
        Instant createdAt,
        String lastMessageBody,
        Instant lastMessageAt) {

    public static ChatConversationResponse from(
            ChatConversation c,
            String driverName,
            String tenantName,
            String vehiclePlate,
            String lastMessageBody,
            Instant lastMessageAt) {
        return new ChatConversationResponse(
                c.getId(), c.getDriverId(), driverName, tenantName, c.getVehicleId(), vehiclePlate,
                c.getCreatedAt(), lastMessageBody, lastMessageAt);
    }
}
