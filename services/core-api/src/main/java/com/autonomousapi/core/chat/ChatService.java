package com.autonomousapi.core.chat;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.routeplan.RoutePlanService;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.tenant.TenantRepository;
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
    private final TenantRepository tenants;
    private final CurrentDriverResolver driverResolver;
    private final PushNotificationService pushNotificationService;
    private final RoutePlanService routePlanService;
    private final TypingIndicatorService typingIndicator;

    public ChatService(
            ChatConversationRepository conversations,
            ChatMessageRepository messages,
            ChatSyncCursorRepository syncCursors,
            DriverRepository drivers,
            VehicleRepository vehicles,
            TenantRepository tenants,
            CurrentDriverResolver driverResolver,
            PushNotificationService pushNotificationService,
            RoutePlanService routePlanService,
            TypingIndicatorService typingIndicator) {
        this.conversations = conversations;
        this.messages = messages;
        this.syncCursors = syncCursors;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.tenants = tenants;
        this.driverResolver = driverResolver;
        this.pushNotificationService = pushNotificationService;
        this.routePlanService = routePlanService;
        this.typingIndicator = typingIndicator;
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

        return ChatConversationResponse.from(
                conversation, driver.getName(), tenantName(tenantId), vehicle != null ? vehicle.getPlate() : null,
                null, null);
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

    /**
     * Gestor-only: anexa uma rota já cadastrada à conversa. Delega em
     * {@link RoutePlanService#assignDriver} — que já cobre os três casos (sem motorista →
     * designa o motorista desta conversa; mesmo motorista → no-op idempotente; motorista
     * diferente → lança conflito explícito, propagado daqui pra fora sem engolir). Só grava
     * a mensagem estruturada se a designação não lançar exceção.
     */
    @Transactional
    public ChatMessageResponse sendRoutePlanMessage(JwtPrincipal gestorPrincipal, UUID conversationId, UUID routePlanId) {
        ChatConversation conversation = findAsParticipant(gestorPrincipal, conversationId);
        RoutePlanResponse plan = routePlanService.assignDriver(gestorPrincipal, routePlanId, conversation.getDriverId());

        String body = "Nova rota atribuída — " + plan.stops().size() + " parada(s).";
        ChatMessage message = messages.save(
                new ChatMessage(conversation.getId(), gestorPrincipal.userId(), body, ChatMessageType.ATRIBUICAO_ROTA, routePlanId));

        UUID recipientUserId = drivers.findById(conversation.getDriverId()).map(Driver::getAppUserId).orElse(null);
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Nova rota atribuída", body);
        }

        return ChatMessageResponse.from(message);
    }

    /**
     * Marca como lidas todas as mensagens do outro participante que ainda não tinham
     * {@code lidoEm} — chamado quando o usuário abre/revisita a conversa. Idempotente
     * (mensagem já lida não é tocada de novo).
     */
    @Transactional
    public void markAsRead(JwtPrincipal principal, UUID conversationId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        messages.findAllByConversationIdAndSenderUserIdNotAndLidoEmIsNull(conversation.getId(), principal.userId())
                .forEach(ChatMessage::marcarComoLida);
    }

    /**
     * Indicador de "digitando" (spec 07): estado puramente efêmero, guardado em memória
     * ({@link TypingIndicatorService}), não em banco — reinicia sozinho se o servidor
     * reiniciar, e isso é aceitável pro que é (um sinal que expira em segundos de qualquer
     * jeito). Não justifica WebSocket/SSE (mesma lógica de "poll simples" já aplicada ao
     * envio/recebimento de mensagem, spec 07).
     */
    @Transactional(readOnly = true)
    public void registerTyping(JwtPrincipal principal, UUID conversationId) {
        findAsParticipant(principal, conversationId);
        typingIndicator.markTyping(conversationId, principal.userId());
    }

    @Transactional(readOnly = true)
    public boolean isOtherParticipantTyping(JwtPrincipal principal, UUID conversationId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        UUID otherUserId = principal.userId().equals(conversation.getGestorUserId())
                ? drivers.findById(conversation.getDriverId()).map(Driver::getAppUserId).orElse(null)
                : conversation.getGestorUserId();
        return otherUserId != null && typingIndicator.isTyping(conversationId, otherUserId);
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
        Optional<ChatMessage> last = messages.findFirstByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(c.getId());
        return ChatConversationResponse.from(
                c, driverName, tenantName(c.getTenantId()), vehiclePlate,
                last.map(ChatMessage::getBody).orElse(null), last.map(ChatMessage::getSentAt).orElse(null));
    }

    private String tenantName(UUID tenantId) {
        return tenants.findById(tenantId).map(t -> t.getName()).orElse(null);
    }

    private boolean isMotorista(JwtPrincipal principal) {
        return "MOTORISTA".equals(principal.role());
    }

    /** Corpo do push não precisa da mensagem inteira — só um preview curto. */
    private static String preview(String body) {
        return body.length() <= 100 ? body : body.substring(0, 97) + "...";
    }
}
