package com.autonomousapi.core.chat;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.chat.dto.ChatReactionResponse;
import com.autonomousapi.core.chat.dto.TeamMemberOptionResponse;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.ChatMessageActionInvalidException;
import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.routeplan.RoutePlanService;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** Prazo pra editar/excluir a própria mensagem, contado de {@code sentAt} — pedido do
     *  Guilherme, editar/apagar algo de horas atrás confunde quem já leu e reagiu ao
     *  original. Excluir tem folga maior que editar de propósito (arrependimento de "mandei
     *  a mensagem errada" é mais comum que "preciso corrigir o texto" depois de um tempo). */
    private static final java.time.Duration EDIT_WINDOW = java.time.Duration.ofMinutes(20);
    private static final java.time.Duration DELETE_WINDOW = java.time.Duration.ofMinutes(35);

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatMessageReactionRepository reactions;
    private final ChatSyncCursorRepository syncCursors;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final CurrentDriverResolver driverResolver;
    private final PushNotificationService pushNotificationService;
    private final RoutePlanService routePlanService;
    private final TypingIndicatorService typingIndicator;

    public ChatService(
            ChatConversationRepository conversations,
            ChatMessageRepository messages,
            ChatMessageReactionRepository reactions,
            ChatSyncCursorRepository syncCursors,
            DriverRepository drivers,
            VehicleRepository vehicles,
            TenantRepository tenants,
            UserRepository users,
            CurrentDriverResolver driverResolver,
            PushNotificationService pushNotificationService,
            RoutePlanService routePlanService,
            TypingIndicatorService typingIndicator) {
        this.conversations = conversations;
        this.messages = messages;
        this.reactions = reactions;
        this.syncCursors = syncCursors;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.tenants = tenants;
        this.users = users;
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

    /**
     * V33, chat em equipe: qualquer membro (GESTOR_FROTA/ADMIN/DESPACHANTE/VISUALIZADOR)
     * pode conversar com qualquer outro do mesmo tenant — ideia registrada desde a spec 07/
     * ADR 0015, implementada agora como conversa aditiva (mesmo {@code chat_conversation},
     * {@code kind = EQUIPE}). Motorista fica de fora (a conversa dele já tem seu próprio
     * caminho, gestor↔motorista). Idempotente por par, ordem canônica evita duas linhas
     * pro mesmo par em chamadas concorrentes ou nas duas direções.
     */
    @Transactional
    public ChatConversationResponse getOrCreateTeamConversation(JwtPrincipal principal, UUID otherUserId) {
        if (otherUserId.equals(principal.userId())) {
            throw new ChatMessageActionInvalidException("Não é possível iniciar uma conversa consigo mesmo.");
        }
        User other = Lookups.orNotFound(
                users.findByIdAndTenantId(otherUserId, principal.tenantId()), "Membro da equipe não encontrado.");
        if (other.getRole() == Role.MOTORISTA) {
            throw new ChatMessageActionInvalidException(
                    "Motorista usa a conversa gestor-motorista, não o chat de equipe.");
        }

        UUID a = principal.userId().compareTo(otherUserId) <= 0 ? principal.userId() : otherUserId;
        UUID b = principal.userId().compareTo(otherUserId) <= 0 ? otherUserId : principal.userId();
        ChatConversation conversation = conversations
                .findByTenantIdAndKindAndGestorUserIdAndParticipantBUserId(
                        principal.tenantId(), ChatConversationKind.EQUIPE, a, b)
                .orElseGet(() -> conversations.save(
                        ChatConversation.novaConversaEquipe(principal.tenantId(), a, b)));

        return toResponse(conversation, principal.userId());
    }

    /** V33 — lista quem dá pra iniciar uma conversa de equipe: mesmo tenant, habilitado,
     *  qualquer papel exceto MOTORISTA, exceto o próprio usuário. */
    @Transactional(readOnly = true)
    public List<TeamMemberOptionResponse> listTeamMembers(JwtPrincipal principal) {
        return users.findAllByTenantIdAndRoleIn(
                        principal.tenantId(), List.of(Role.GESTOR_FROTA, Role.ADMIN, Role.DESPACHANTE, Role.VISUALIZADOR))
                .stream()
                .filter(u -> u.isEnabled() && !u.getId().equals(principal.userId()))
                .map(u -> new TeamMemberOptionResponse(u.getId(), u.getEmail(), u.getRole().name()))
                .toList();
    }

    /** Gestor vê as próprias conversas (gestor-motorista + as de equipe em que é o
     *  "participante A"); motorista vê as conversas em que é o motorista; qualquer membro
     *  de equipe também vê as conversas de equipe em que é o "participante B" (V33). */
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listConversations(JwtPrincipal principal) {
        if (isMotorista(principal)) {
            return toResponses(conversations.findAllByDriverIdOrderByCreatedAtDesc(driverResolver.resolve(principal).getId()), principal.userId());
        }
        List<ChatConversation> list = new ArrayList<>(conversations.findAllByGestorUserIdOrderByCreatedAtDesc(principal.userId()));
        list.addAll(conversations.findAllByKindAndParticipantBUserId(ChatConversationKind.EQUIPE, principal.userId()));
        list.sort(Comparator.comparing(ChatConversation::getCreatedAt).reversed());
        return toResponses(list, principal.userId());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(JwtPrincipal principal, UUID conversationId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        List<ChatMessage> list =
                messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtAsc(conversation.getId());
        java.util.Map<UUID, List<ChatReactionResponse>> reactionsByMessage = reactionsByMessage(list);
        return list.stream()
                .map(m -> ChatMessageResponse.from(m, reactionsByMessage.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    /** Batch pra não fazer N+1 (mesmo padrão de {@link #toResponses}). */
    private java.util.Map<UUID, List<ChatReactionResponse>> reactionsByMessage(List<ChatMessage> list) {
        if (list.isEmpty()) {
            return java.util.Map.of();
        }
        List<UUID> ids = list.stream().map(ChatMessage::getId).toList();
        return reactions.findAllByMessageIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ChatMessageReaction::getMessageId,
                        java.util.stream.Collectors.mapping(ChatReactionResponse::from, java.util.stream.Collectors.toList())));
    }

    /** Envia e notifica por push o outro participante da conversa. {@code replyToMessageId}
     *  opcional (V31): copia um retrato do texto/autor original pra dentro da mensagem nova,
     *  sem depender de a original ainda estar na janela de retenção depois. */
    @Transactional
    public ChatMessageResponse sendMessage(JwtPrincipal principal, UUID conversationId, String body, UUID replyToMessageId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        ChatMessage message = new ChatMessage(conversation.getId(), principal.userId(), body);
        if (replyToMessageId != null) {
            ChatMessage original = Lookups.orNotFound(
                    messages.findByIdAndConversationId(replyToMessageId, conversation.getId()), "Mensagem não encontrada.");
            if (original.getDeletedAt() != null) {
                throw new ChatMessageActionInvalidException("Mensagem excluída não pode ser respondida.");
            }
            message.responderA(original.getId(), preview(original.getBody()), original.getSenderUserId());
        }
        messages.save(message);

        UUID recipientUserId = otherParticipantUserId(conversation, principal.userId());
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Nova mensagem", preview(body));
        }

        return ChatMessageResponse.from(message);
    }

    /** Só o autor, só {@code TEXTO}, só enquanto ainda na janela de retenção (V31) — fora
     *  disso o outro lado não teria como ver a edição (poll só busca {@code aindaNoServidor}).
     *  Também só até {@link #EDIT_WINDOW} depois de enviada (pedido do Guilherme — editar
     *  uma mensagem de horas atrás confunde quem já leu e reagiu ao texto original). */
    @Transactional
    public ChatMessageResponse editMessage(JwtPrincipal principal, UUID conversationId, UUID messageId, String novoBody) {
        ChatMessage message = findEditableOwnMessage(principal, conversationId, messageId);
        if (Instant.now().isAfter(message.getSentAt().plus(EDIT_WINDOW))) {
            throw new ChatMessageActionInvalidException("Prazo pra editar esta mensagem já passou (20 minutos).");
        }
        message.editar(novoBody);
        return ChatMessageResponse.from(message);
    }

    /** Mesmas guardas de {@link #editMessage} (autor, TEXTO, ainda retida), mas com janela
     *  de tempo própria — {@link #DELETE_WINDOW}, mais folgada que a de editar. */
    @Transactional
    public ChatMessageResponse deleteMessage(JwtPrincipal principal, UUID conversationId, UUID messageId) {
        ChatMessage message = findEditableOwnMessage(principal, conversationId, messageId);
        if (Instant.now().isAfter(message.getSentAt().plus(DELETE_WINDOW))) {
            throw new ChatMessageActionInvalidException("Prazo pra excluir esta mensagem já passou (35 minutos).");
        }
        message.apagar();
        return ChatMessageResponse.from(message);
    }

    private ChatMessage findEditableOwnMessage(JwtPrincipal principal, UUID conversationId, UUID messageId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        ChatMessage message = Lookups.orNotFound(
                messages.findByIdAndConversationId(messageId, conversation.getId()), "Mensagem não encontrada.");
        if (!message.getSenderUserId().equals(principal.userId())) {
            throw new ChatMessageActionInvalidException("Só quem enviou pode editar ou excluir esta mensagem.");
        }
        if (message.getMessageType() != ChatMessageType.TEXTO) {
            throw new ChatMessageActionInvalidException("Só mensagens de texto podem ser editadas ou excluídas.");
        }
        if (!message.isAindaNoServidor()) {
            throw new ChatMessageActionInvalidException("Mensagem antiga demais — já saiu da janela de retenção do servidor.");
        }
        if (message.getDeletedAt() != null) {
            throw new ChatMessageActionInvalidException("Mensagem já foi excluída.");
        }
        return message;
    }

    /**
     * Encaminha o texto de uma mensagem pra outra conversa da mesma pessoa — valida
     * participação nas DUAS conversas (reusa {@link #findAsParticipant} pros dois lados, o
     * que já cobre sozinho o caso de alguém tentar encaminhar pra uma conversa que não é
     * dela). Sem a restrição de janela de {@link #editMessage}: cria uma mensagem NOVA, não
     * depende de a original ainda estar retida.
     */
    @Transactional
    public ChatMessageResponse forwardMessage(
            JwtPrincipal principal, UUID sourceConversationId, UUID messageId, UUID targetConversationId) {
        ChatConversation source = findAsParticipant(principal, sourceConversationId);
        ChatMessage original = Lookups.orNotFound(
                messages.findByIdAndConversationId(messageId, source.getId()), "Mensagem não encontrada.");
        if (original.getDeletedAt() != null) {
            throw new ChatMessageActionInvalidException("Mensagem excluída não pode ser encaminhada.");
        }
        ChatConversation target = findAsParticipant(principal, targetConversationId);

        ChatMessage forwarded = new ChatMessage(target.getId(), principal.userId(), original.getBody());
        forwarded.marcarComoEncaminhada(original.getId());
        messages.save(forwarded);

        UUID recipientUserId = otherParticipantUserId(target, principal.userId());
        if (recipientUserId != null) {
            pushNotificationService.notifyUser(recipientUserId, "Nova mensagem", preview(original.getBody()));
        }
        return ChatMessageResponse.from(forwarded);
    }

    /** Upsert — substitui a reação anterior desta pessoa nesta mensagem, se houver (igual
     *  WhatsApp: tocar em outro emoji troca, tocar no mesmo remove via {@link #removeReaction}). */
    @Transactional
    public List<ChatReactionResponse> reactToMessage(
            JwtPrincipal principal, UUID conversationId, UUID messageId, String emoji) {
        ChatMessage message = findReactableMessage(principal, conversationId, messageId);
        reactions.findByMessageIdAndUserId(message.getId(), principal.userId()).ifPresent(reactions::delete);
        reactions.save(new ChatMessageReaction(message.getId(), principal.userId(), emoji));
        return reactions.findAllByMessageIdIn(List.of(message.getId())).stream()
                .map(ChatReactionResponse::from)
                .toList();
    }

    @Transactional
    public List<ChatReactionResponse> removeReaction(JwtPrincipal principal, UUID conversationId, UUID messageId) {
        ChatMessage message = findReactableMessage(principal, conversationId, messageId);
        reactions.deleteByMessageIdAndUserId(message.getId(), principal.userId());
        return reactions.findAllByMessageIdIn(List.of(message.getId())).stream()
                .map(ChatReactionResponse::from)
                .toList();
    }

    private ChatMessage findReactableMessage(JwtPrincipal principal, UUID conversationId, UUID messageId) {
        ChatConversation conversation = findAsParticipant(principal, conversationId);
        ChatMessage message = Lookups.orNotFound(
                messages.findByIdAndConversationId(messageId, conversation.getId()), "Mensagem não encontrada.");
        if (!message.isAindaNoServidor()) {
            throw new ChatMessageActionInvalidException("Mensagem antiga demais — já saiu da janela de retenção do servidor.");
        }
        return message;
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
        requireGestorMotorista(conversation);
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
        requireGestorMotorista(conversation);
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
        requireGestorMotorista(conversation);
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
        UUID otherUserId = otherParticipantUserId(conversation, principal.userId());
        return otherUserId != null && typingIndicator.isTyping(conversationId, otherUserId);
    }

    /** Generaliza "quem é o outro lado desta conversa" pros dois kinds (V33) — usado por
     *  sendMessage/forwardMessage/isOtherParticipantTyping/toResponses. */
    private UUID otherParticipantUserId(ChatConversation c, UUID viewerUserId) {
        if (c.getKind() == ChatConversationKind.EQUIPE) {
            return viewerUserId.equals(c.getGestorUserId()) ? c.getParticipantBUserId() : c.getGestorUserId();
        }
        return viewerUserId.equals(c.getGestorUserId())
                ? drivers.findById(c.getDriverId()).map(Driver::getAppUserId).orElse(null)
                : c.getGestorUserId();
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

    /** V33 — as ações de rota (anexar/cancelar/trocar) só existem no chat gestor↔motorista;
     *  conversa de equipe não tem motorista pra atribuir/notificar. Erro explícito em vez de
     *  deixar {@code RoutePlanService} falhar com "motorista não encontrado" (driverId nulo). */
    private void requireGestorMotorista(ChatConversation conversation) {
        if (conversation.getKind() != ChatConversationKind.GESTOR_MOTORISTA) {
            throw new ChatMessageActionInvalidException("Ações de rota só existem na conversa com o motorista.");
        }
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

    private ChatConversationResponse toResponse(ChatConversation c, UUID viewerUserId) {
        return toResponses(List.of(c), viewerUserId).get(0);
    }

    /**
     * Resolve nomes de motorista/veículo/tenant/e-mail e a última mensagem de cada conversa
     * com uma query cada, em vez de várias por conversa (evita N+1). {@code viewerUserId}
     * decide, pra conversas EQUIPE, qual dos dois lados do par é "o outro participante"
     * (V33) — pra GESTOR_MOTORISTA não faz diferença nenhuma.
     */
    private List<ChatConversationResponse> toResponses(List<ChatConversation> list, UUID viewerUserId) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> driverIds = list.stream().map(ChatConversation::getDriverId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<UUID> vehicleIds = list.stream().map(ChatConversation::getVehicleId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<UUID> tenantIds = list.stream().map(ChatConversation::getTenantId).distinct().toList();
        List<UUID> conversationIds = list.stream().map(ChatConversation::getId).toList();
        List<UUID> otherParticipantIds = list.stream()
                .filter(c -> c.getKind() == ChatConversationKind.EQUIPE)
                .map(c -> otherParticipantUserId(c, viewerUserId))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        java.util.Map<UUID, String> driverNames = drivers.findAllById(driverIds).stream()
                .collect(java.util.stream.Collectors.toMap(Driver::getId, Driver::getName));
        java.util.Map<UUID, String> vehiclePlates = vehicles.findAllById(vehicleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Vehicle::getId, Vehicle::getPlate));
        java.util.Map<UUID, String> tenantNames = tenants.findAllById(tenantIds).stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.getId(), t -> t.getName()));
        java.util.Map<UUID, User> otherParticipants = users.findAllById(otherParticipantIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        java.util.Map<UUID, ChatMessage> lastMessageByConversation =
                messages.findAllByConversationIdInAndAindaNoServidorTrueOrderBySentAtDesc(conversationIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ChatMessage::getConversationId, m -> m, (first, dup) -> first));

        return list.stream()
                .map(c -> {
                    ChatMessage last = lastMessageByConversation.get(c.getId());
                    String lastBody = last != null ? last.getBody() : null;
                    Instant lastAt = last != null ? last.getSentAt() : null;
                    if (c.getKind() == ChatConversationKind.EQUIPE) {
                        User other = otherParticipants.get(otherParticipantUserId(c, viewerUserId));
                        return ChatConversationResponse.fromEquipe(
                                c, other != null ? other.getId() : null, other != null ? other.getEmail() : null,
                                other != null ? other.getRole().name() : null, lastBody, lastAt);
                    }
                    return ChatConversationResponse.from(
                            c,
                            driverNames.get(c.getDriverId()),
                            tenantNames.get(c.getTenantId()),
                            c.getVehicleId() != null ? vehiclePlates.get(c.getVehicleId()) : null,
                            lastBody, lastAt);
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
