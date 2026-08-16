package com.autonomousapi.core.chat;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mini-chat gestor↔motorista (spec 07, ADR 0015). Uma conversa por par (gestor, motorista);
 * mensagens circulam só dentro desse par — nunca motorista com motorista, nunca fora do
 * tenant. O servidor é canal de entrega + janela curta, não arquivo — a limpeza é o
 * {@link ChatCleanupJob}, não este service.
 */
@Service
public class ChatService {

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatSyncCursorRepository syncCursors;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final CurrentDriverResolver driverResolver;
    private final PushNotificationService pushNotificationService;

    public ChatService(
            ChatConversationRepository conversations,
            ChatMessageRepository messages,
            ChatSyncCursorRepository syncCursors,
            DriverRepository drivers,
            VehicleRepository vehicles,
            CurrentDriverResolver driverResolver,
            PushNotificationService pushNotificationService) {
        this.conversations = conversations;
        this.messages = messages;
        this.syncCursors = syncCursors;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.driverResolver = driverResolver;
        this.pushNotificationService = pushNotificationService;
    }

    /** Gestor-only. Idempotente: se a conversa já existe para o par, devolve a existente. */
    @Transactional
    public ChatConversationResponse getOrCreateConversation(
            JwtPrincipal gestorPrincipal, UUID driverId, UUID vehicleId) {
        UUID tenantId = gestorPrincipal.tenantId();
        Driver driver = drivers.findByIdAndTenantId(driverId, tenantId)
                .orElseThrow(() -> new NotFoundException("Motorista não encontrado."));
        if (!driver.hasLogin()) {
            throw new DriverWithoutLoginException();
        }
        Vehicle vehicle = null;
        if (vehicleId != null) {
            vehicle = vehicles.findByIdAndTenantId(vehicleId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
        }

        ChatConversation conversation = conversations
                .findByGestorUserIdAndDriverId(gestorPrincipal.userId(), driverId)
                .orElseGet(() -> conversations.save(
                        new ChatConversation(tenantId, gestorPrincipal.userId(), driverId, vehicleId)));

        return ChatConversationResponse.from(conversation, driver.getName(), vehicle != null ? vehicle.getPlate() : null);
    }

    /** Gestor vê as próprias conversas; motorista vê as conversas em que é o motorista. */
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listConversations(JwtPrincipal principal) {
        List<ChatConversation> list = isMotorista(principal)
                ? conversations.findAllByDriverIdOrderByCreatedAtDesc(driverResolver.resolve(principal).getId())
                : conversations.findAllByGestorUserIdOrderByCreatedAtDesc(principal.userId());

        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(JwtPrincipal principal, UUID conversationId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        return messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtAsc(conversation.getId()).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /** Envia e notifica por push o outro participante da conversa. */
    @Transactional
    public ChatMessageResponse sendMessage(JwtPrincipal principal, UUID conversationId, String body) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        ChatMessage message = messages.save(new ChatMessage(conversation.getId(), principal.userId(), body));

        UUID recipientUserId = principal.userId().equals(conversation.getGestorUserId())
                ? drivers.findById(conversation.getDriverId()).map(Driver::getAppUserId).orElse(null)
                : conversation.getGestorUserId();
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Nova mensagem", preview(body));
        }

        return ChatMessageResponse.from(message);
    }

    /** Gestor-only: confirma que o device já persistiu localmente tudo até syncedAt. */
    @Transactional
    public void registerSyncCursor(JwtPrincipal gestorPrincipal, String deviceId, Instant syncedAt) {
        ChatSyncCursor cursor = syncCursors
                .findByGestorUserIdAndDeviceId(gestorPrincipal.userId(), deviceId)
                .orElseGet(() -> new ChatSyncCursor(gestorPrincipal.userId(), deviceId, syncedAt));
        cursor.avancar(syncedAt);
        syncCursors.save(cursor);
    }

    private ChatConversation findAsParticipant(JwtPrincipal principal, UUID conversationId) {
        ChatConversation conversation = conversations
                .findByIdAndTenantId(conversationId, principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Conversa não encontrada."));

        UUID participantDriverId = isMotorista(principal) ? driverResolver.resolve(principal).getId() : null;
        if (!conversation.hasParticipant(principal.userId(), participantDriverId)) {
            // Mesma resposta de "não existe" — não revela que a conversa existe para quem
            // não participa dela.
            throw new NotFoundException("Conversa não encontrada.");
        }
        return conversation;
    }

    private ChatConversationResponse toResponse(ChatConversation c) {
        String driverName = drivers.findById(c.getDriverId()).map(Driver::getName).orElse(null);
        String vehiclePlate = c.getVehicleId() != null
                ? vehicles.findById(c.getVehicleId()).map(Vehicle::getPlate).orElse(null)
                : null;
        return ChatConversationResponse.from(c, driverName, vehiclePlate);
    }

    private boolean isMotorista(JwtPrincipal principal) {
        return "MOTORISTA".equals(principal.role());
    }

    /** Corpo do push não precisa da mensagem inteira — só um preview curto. */
    private static String preview(String body) {
        return body.length() <= 100 ? body : body.substring(0, 97) + "...";
    }
}
