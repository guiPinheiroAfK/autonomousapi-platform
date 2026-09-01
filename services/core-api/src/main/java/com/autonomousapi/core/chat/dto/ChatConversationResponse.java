package com.autonomousapi.core.chat.dto;

import com.autonomousapi.core.chat.ChatConversation;
import com.autonomousapi.core.chat.ChatConversationKind;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code tenantName} — não "gestorName": {@code app_user} não tem coluna de nome de pessoa
 * (só {@code email}), então o que o motorista vê do outro lado é o nome da frota/empresa
 * ({@code tenant.name}), nunca um nome próprio. Nomear certo evita reabrir essa dúvida
 * depois (mesma armadilha do "OS de manutenção" vs. "OS de rota" no spec 07).
 *
 * <p>{@code kind}/{@code otherParticipantEmail}/{@code otherParticipantRole} (V33, chat em
 * equipe): só preenchidos quando {@code kind == EQUIPE} — mesmo raciocínio de
 * {@code tenantName}, o outro lado é identificado pelo que existe (e-mail), não um nome
 * próprio inexistente no cadastro.
 */
public record ChatConversationResponse(
        UUID id,
        String kind,
        UUID driverId,
        String driverName,
        String tenantName,
        UUID vehicleId,
        String vehiclePlate,
        UUID otherParticipantUserId,
        String otherParticipantEmail,
        String otherParticipantRole,
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
                c.getId(), c.getKind().name(), c.getDriverId(), driverName, tenantName, c.getVehicleId(), vehiclePlate,
                null, null, null, c.getCreatedAt(), lastMessageBody, lastMessageAt);
    }

    public static ChatConversationResponse fromEquipe(
            ChatConversation c,
            UUID otherParticipantUserId,
            String otherParticipantEmail,
            String otherParticipantRole,
            String lastMessageBody,
            Instant lastMessageAt) {
        return new ChatConversationResponse(
                c.getId(), ChatConversationKind.EQUIPE.name(), null, null, null, null, null,
                otherParticipantUserId, otherParticipantEmail, otherParticipantRole,
                c.getCreatedAt(), lastMessageBody, lastMessageAt);
    }
}
