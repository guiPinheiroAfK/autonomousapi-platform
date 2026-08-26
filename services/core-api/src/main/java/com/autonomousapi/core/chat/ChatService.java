package com.autonomousapi.core.chat;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.Lookups;
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
        Driver driver = Lookups.orNotFound(drivers.findByIdAndTenantId(driverId, tenantId), "Motorista não encontrado.");
        if (!driver.hasLogin()) {
            throw new DriverWithoutLoginException();
        }
        Vehicle vehicle = null;
        if (vehicleId != null) {
            vehicle = Lookups.orNotFound(vehicles.findByIdAndTenantId(vehicleId, tenantId), "Veículo não encontrado.");
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

        return toResponses(list);
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
        RoutePlanResponse plan = routePlanService.assignDriver(
                gestorPrincipal, routePlanId, conversation.getDriverId(), false, "chat");

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
     * Gestor-only: cancela uma rota já EM_ANDAMENTO pelo chat (ADR 0021) — a tela de Rotas
     * só cancela PLANEJADA; cancelar no meio do trâmite passa por aqui de propósito, fica
     * registrado na conversa com o motorista. Delega em {@link RoutePlanService#cancel}, que
     * também aceita PLANEJADA (idêntico ao cancelamento direto, só que registrado no chat).
     */
    @Transactional
    public ChatMessageResponse sendCancelamentoMessage(JwtPrincipal gestorPrincipal, UUID conversationId, UUID routePlanId) {
        ChatConversation conversation = findAsParticipant(gestorPrincipal, conversationId);
        routePlanService.cancel(gestorPrincipal, routePlanId, true);

        String body = "Rota cancelada.";
        ChatMessage message = messages.save(
                new ChatMessage(conversation.getId(), gestorPrincipal.userId(), body, ChatMessageType.CANCELAMENTO_ROTA, routePlanId));

        UUID recipientUserId = drivers.findById(conversation.getDriverId()).map(Driver::getAppUserId).orElse(null);
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Rota cancelada", body);
        }
        return ChatMessageResponse.from(message);
    }

    /**
     * Gestor-only: reatribui a rota a outro motorista, pelo chat da conversa em que a
     * solicitação de troca chegou (ADR 0021) — {@code forcar=true} é só chamado aqui, nunca
     * solto em outro caminho. A conversa usada pro registro é a do motorista NOVO — a
     * mensagem "você recebeu uma rota" precisa aparecer na conversa dele, não na de quem
     * está saindo.
     */
    @Transactional
    public ChatMessageResponse sendTrocaMotoristaMessage(
            JwtPrincipal gestorPrincipal, UUID conversationIdNovoMotorista, UUID routePlanId) {
        ChatConversation conversation = findAsParticipant(gestorPrincipal, conversationIdNovoMotorista);
        routePlanService.assignDriver(gestorPrincipal, routePlanId, conversation.getDriverId(), true, "chat");

        String body = "Rota transferida pra você.";
        ChatMessage message = messages.save(
                new ChatMessage(conversation.getId(), gestorPrincipal.userId(), body, ChatMessageType.TROCA_MOTORISTA, routePlanId));

        UUID recipientUserId = drivers.findById(conversation.getDriverId()).map(Driver::getAppUserId).orElse(null);
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Nova rota atribuída", body);
        }
        return ChatMessageResponse.from(message);
    }

    /**
     * Motorista-only: solicita cancelamento da rota atribuída — nunca cancela sozinho, só
     * avisa o gestor (ADR 0021: "motorista não decide sozinho, só solicita"). Enquanto
     * pendente, a rota continua ativa normalmente — a solicitação não trava o fluxo
     * principal, só é informativa pro gestor decidir. {@code routePlanId} vem de
     * {@link RoutePlanService#activeForDriver}, nunca do que o cliente manda — evita
     * referenciar uma rota que não é a ativa do próprio motorista.
     */
    @Transactional
    public ChatMessageResponse solicitarCancelamento(JwtPrincipal motoristaPrincipal, UUID conversationId) {
        return solicitar(
                motoristaPrincipal, conversationId,
                ChatMessageType.SOLICITACAO_CANCELAMENTO, "Motorista solicitou cancelamento da rota.", "Solicitação de cancelamento");
    }

    /** Motorista-only: solicita passar a rota atribuída pra outra pessoa — mesmo espírito
     *  de {@link #solicitarCancelamento}, o gestor decide (via {@link #sendTrocaMotoristaMessage}
     *  na conversa do novo motorista, ou recusando com uma mensagem comum). */
    @Transactional
    public ChatMessageResponse solicitarTrocaMotorista(JwtPrincipal motoristaPrincipal, UUID conversationId) {
        return solicitar(
                motoristaPrincipal, conversationId,
                ChatMessageType.SOLICITACAO_TROCA_MOTORISTA, "Motorista solicitou passar a rota pra outra pessoa.",
                "Solicitação de troca de motorista");
    }

    private ChatMessageResponse solicitar(
            JwtPrincipal motoristaPrincipal, UUID conversationId, ChatMessageType tipo, String body, String tituloPush) {
        ChatConversation conversation = findAsParticipant(motoristaPrincipal, conversationId);
        RoutePlanResponse rotaAtiva = routePlanService.activeForDriver(motoristaPrincipal);
        UUID routePlanId = rotaAtiva != null ? rotaAtiva.id() : null;

        ChatMessage message = messages.save(
                new ChatMessage(conversation.getId(), motoristaPrincipal.userId(), body, tipo, routePlanId));

        pushNotificationService.notifyUser(conversation.getGestorUserId(), tituloPush, body);
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
        ChatConversation conversation = Lookups.orNotFound(
                conversations.findByIdAndTenantId(conversationId, principal.tenantId()), "Conversa não encontrada.");

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

    /**
     * Versão em lote de {@link #toResponse}: resolve nomes de motorista/veículo/tenant e a
     * última mensagem de cada conversa com uma query cada, em vez de 3 por conversa
     * (ver {@code ChatMessageRepository#findAllByConversationIdInAndAindaNoServidorTrueOrderBySentAtDesc}).
     */
    private List<ChatConversationResponse> toResponses(List<ChatConversation> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> driverIds = list.stream().map(ChatConversation::getDriverId).distinct().toList();
        List<UUID> vehicleIds = list.stream().map(ChatConversation::getVehicleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<UUID> tenantIds = list.stream().map(ChatConversation::getTenantId).distinct().toList();
        List<UUID> conversationIds = list.stream().map(ChatConversation::getId).toList();

        java.util.Map<UUID, String> driverNames = drivers.findAllById(driverIds).stream()
                .collect(java.util.stream.Collectors.toMap(Driver::getId, Driver::getName));
        java.util.Map<UUID, String> vehiclePlates = vehicles.findAllById(vehicleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Vehicle::getId, Vehicle::getPlate));
        java.util.Map<UUID, String> tenantNames = tenants.findAllById(tenantIds).stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.getId(), t -> t.getName()));
        java.util.Map<UUID, ChatMessage> lastMessageByConversation =
                messages.findAllByConversationIdInAndAindaNoServidorTrueOrderBySentAtDesc(conversationIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ChatMessage::getConversationId, m -> m, (first, dup) -> first));

        return list.stream()
                .map(c -> {
                    ChatMessage last = lastMessageByConversation.get(c.getId());
                    return ChatConversationResponse.from(
                            c,
                            driverNames.get(c.getDriverId()),
                            tenantNames.get(c.getTenantId()),
                            c.getVehicleId() != null ? vehiclePlates.get(c.getVehicleId()) : null,
                            last != null ? last.getBody() : null,
                            last != null ? last.getSentAt() : null);
                })
                .toList();
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
