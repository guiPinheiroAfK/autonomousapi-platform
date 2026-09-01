package com.autonomousapi.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.chat.dto.ChatReactionResponse;
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.error.ChatMessageActionInvalidException;
import com.autonomousapi.core.error.DriverWithoutLoginException;
import com.autonomousapi.core.error.NotFoundException;
import com.autonomousapi.core.error.RoutePlanAlreadyAssignedException;
import com.autonomousapi.core.push.PushNotificationService;
import com.autonomousapi.core.routeplan.RoutePlanService;
import com.autonomousapi.core.routeplan.RoutePlanStatus;
import com.autonomousapi.core.routeplan.dto.RoutePlanResponse;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.UserRepository;
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    /** Mesmo padrão de {@code ChatCleanupJobTest} pra "enviar" uma mensagem no passado —
     *  o construtor de {@link ChatMessage} sempre usa {@code Instant.now()}, sem isso não
     *  dá pra testar a janela de tempo de editar/excluir. */
    private static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatMessageReactionRepository reactions = mock(ChatMessageReactionRepository.class);
    private final ChatSyncCursorRepository syncCursors = mock(ChatSyncCursorRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentDriverResolver driverResolver = mock(CurrentDriverResolver.class);
    private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);
    private final RoutePlanService routePlanService = mock(RoutePlanService.class);
    private final TypingIndicatorService typingIndicator = new TypingIndicatorService();

    private final ChatService service = new ChatService(
            conversations, messages, reactions, syncCursors, drivers, vehicles, tenants, users, driverResolver,
            pushNotificationService, routePlanService, typingIndicator);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID gestorUserId = UUID.randomUUID();
    private final JwtPrincipal gestorPrincipal = new JwtPrincipal(gestorUserId, tenantId, "GESTOR_FROTA");

    private Driver driverComLogin() {
        Driver d = new Driver(tenantId, "João Motorista", "12345678901", null);
        d.linkAppUser(UUID.randomUUID());
        return d;
    }

    @Test
    void rejeitaCriarConversaComMotoristaSemLogin() {
        Driver semLogin = new Driver(tenantId, "Maria", "98765432100", null);
        when(drivers.findByIdAndTenantId(semLogin.getId(), tenantId)).thenReturn(Optional.of(semLogin));

        assertThrows(DriverWithoutLoginException.class,
                () -> service.getOrCreateConversation(gestorPrincipal, semLogin.getId(), null));
        verify(conversations, never()).save(any());
    }

    @Test
    void criaConversaNovaQuandoNaoExiste() {
        Driver d = driverComLogin();
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(conversations.findByGestorUserIdAndDriverId(gestorUserId, d.getId())).thenReturn(Optional.empty());
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChatConversationResponse resp = service.getOrCreateConversation(gestorPrincipal, d.getId(), null);

        assertEquals(d.getId(), resp.driverId());
        assertEquals("João Motorista", resp.driverName());
    }

    @Test
    void getOrCreateEIdempotenteSeJaExiste() {
        Driver d = driverComLogin();
        ChatConversation existente = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        when(drivers.findByIdAndTenantId(d.getId(), tenantId)).thenReturn(Optional.of(d));
        when(conversations.findByGestorUserIdAndDriverId(gestorUserId, d.getId())).thenReturn(Optional.of(existente));

        ChatConversationResponse resp = service.getOrCreateConversation(gestorPrincipal, d.getId(), null);

        assertEquals(existente.getId(), resp.id());
        verify(conversations, never()).save(any());
    }

    @Test
    void listConversationsParaMotoristaUsaDriverResolvido() {
        UUID driverId = UUID.randomUUID();
        Driver d = driverComLogin();
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");
        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(conversations.findAllByDriverIdOrderByCreatedAtDesc(d.getId())).thenReturn(List.of());

        service.listConversations(motoristaPrincipal);

        verify(conversations).findAllByDriverIdOrderByCreatedAtDesc(d.getId());
        verify(conversations, never()).findAllByGestorUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void listMessagesRejeitaNaoParticipante() {
        UUID conversationId = UUID.randomUUID();
        UUID outroGestor = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, outroGestor, UUID.randomUUID(), null);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));

        assertThrows(NotFoundException.class, () -> service.listMessages(gestorPrincipal, conversationId));
    }

    @Test
    void listMessagesFuncionaParaOGestorDaConversa() {
        UUID conversationId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, driverId, null);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findAllByConversationIdAndAindaNoServidorTrueOrderBySentAtAsc(conv.getId()))
                .thenReturn(List.of());

        List<ChatMessageResponse> result = service.listMessages(gestorPrincipal, conversationId);

        assertEquals(0, result.size());
    }

    @Test
    void sendMessageDoGestorNotificaOMotorista() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));

        service.sendMessage(gestorPrincipal, conversationId, "Oi, tudo bem?", null);

        verify(pushNotificationService).notifyUser(eq(d.getAppUserId()), eq("Nova mensagem"), any());
    }

    @Test
    void sendMessageDoMotoristaNotificaOGestor() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(d.getAppUserId(), tenantId, "MOTORISTA");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(motoristaPrincipal, conversationId, "Cheguei na oficina", null);

        verify(pushNotificationService).notifyUser(eq(gestorUserId), eq("Nova mensagem"), any());
    }

    @Test
    void registerSyncCursorCriaNaPrimeiraVez() {
        java.time.Instant now = java.time.Instant.now();
        when(syncCursors.findByGestorUserIdAndDeviceId(gestorUserId, "device-1")).thenReturn(Optional.empty());

        service.registerSyncCursor(gestorPrincipal, "device-1", now);

        verify(syncCursors).save(any(ChatSyncCursor.class));
    }

    @Test
    void listConversationsResolveTenantNamePraOMotoristaVer() {
        UUID driverId = UUID.randomUUID();
        Driver d = driverComLogin();
        // Tenant não expõe construtor com id fixo (gera UUID aleatório no construtor) — usa
        // o id gerado por ele em vez de um tenantId arbitrário, senão o lookup em lote
        // (tenants.findAllById) nunca bate com a chave certa no Map.
        Tenant tenant = new Tenant("Frota Rota Certa");
        ChatConversation conv = new ChatConversation(tenant.getId(), gestorUserId, driverId, null);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenant.getId(), "MOTORISTA");
        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(conversations.findAllByDriverIdOrderByCreatedAtDesc(d.getId())).thenReturn(List.of(conv));
        when(drivers.findAllById(List.of(driverId))).thenReturn(List.of(d));
        when(tenants.findAllById(List.of(tenant.getId()))).thenReturn(List.of(tenant));
        when(messages.findFirstByConversationIdAndAindaNoServidorTrueOrderBySentAtDesc(conv.getId()))
                .thenReturn(Optional.empty());

        List<ChatConversationResponse> result = service.listConversations(motoristaPrincipal);

        assertEquals("Frota Rota Certa", result.get(0).tenantName());
    }

    @Test
    void sendRoutePlanMessageDelegaDesignacaoENotificaOMotorista() {
        UUID conversationId = UUID.randomUUID();
        UUID routePlanId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(routePlanService.assignDriver(gestorPrincipal, routePlanId, d.getId(), false, "chat"))
                .thenReturn(new RoutePlanResponse(routePlanId, d.getId(), d.getName(), null, null,
                        RoutePlanStatus.PLANEJADA, com.autonomousapi.core.routeplan.RouteCategoria.ROTA,
                        java.time.LocalDate.now(), null, null, null, java.time.Instant.now(), List.of(), null));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));

        ChatMessageResponse resp = service.sendRoutePlanMessage(gestorPrincipal, conversationId, routePlanId);

        assertEquals(ChatMessageType.ATRIBUICAO_ROTA, resp.messageType());
        assertEquals(routePlanId, resp.routePlanId());
        verify(pushNotificationService).notifyUser(eq(d.getAppUserId()), eq("Nova rota atribuída"), any());
    }

    @Test
    void sendRoutePlanMessagePropagaConflitoSemGravarMensagem() {
        UUID conversationId = UUID.randomUUID();
        UUID routePlanId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(routePlanService.assignDriver(gestorPrincipal, routePlanId, d.getId(), false, "chat"))
                .thenThrow(new RoutePlanAlreadyAssignedException());

        assertThrows(RoutePlanAlreadyAssignedException.class,
                () -> service.sendRoutePlanMessage(gestorPrincipal, conversationId, routePlanId));
        verify(messages, never()).save(any());
    }

    @Test
    void markAsReadMarcaSoMensagensDoOutroParticipanteAindaNaoLidas() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        ChatMessage doMotorista = new ChatMessage(conv.getId(), d.getAppUserId(), "oi");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findAllByConversationIdAndSenderUserIdNotAndLidoEmIsNull(conv.getId(), gestorUserId))
                .thenReturn(List.of(doMotorista));

        service.markAsRead(gestorPrincipal, conversationId);

        org.junit.jupiter.api.Assertions.assertNotNull(doMotorista.getLidoEm());
    }

    @Test
    void typingIndicatorRefleteDigitandoDoOutroParticipante() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(d.getAppUserId(), tenantId, "MOTORISTA");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));

        assertEquals(false, service.isOtherParticipantTyping(gestorPrincipal, conversationId));

        service.registerTyping(motoristaPrincipal, conversationId);

        assertEquals(true, service.isOtherParticipantTyping(gestorPrincipal, conversationId));
    }

    @Test
    void sendMessageComRespostaCopiaRetratoDaOriginal() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        ChatMessage original = new ChatMessage(conv.getId(), d.getAppUserId(), "Cheguei no ponto A");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(original.getId(), conv.getId())).thenReturn(Optional.of(original));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));

        ChatMessageResponse resp = service.sendMessage(gestorPrincipal, conversationId, "Show, valeu!", original.getId());

        assertEquals(original.getId(), resp.replyToMessageId());
        assertEquals("Cheguei no ponto A", resp.replyToBody());
        assertEquals(d.getAppUserId(), resp.replyToSenderUserId());
    }

    @Test
    void editMessageRejeitaQuemNaoEOAutor() {
        UUID conversationId = UUID.randomUUID();
        Driver d = driverComLogin();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, d.getId(), null);
        ChatMessage doMotorista = new ChatMessage(conv.getId(), d.getAppUserId(), "oi");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(doMotorista.getId(), conv.getId())).thenReturn(Optional.of(doMotorista));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.editMessage(gestorPrincipal, conversationId, doMotorista.getId(), "texto novo"));
    }

    @Test
    void editMessageRejeitaForaDaJanelaDeRetencao() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage antiga = new ChatMessage(conv.getId(), gestorUserId, "oi");
        antiga.removerDoServidor();
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(antiga.getId(), conv.getId())).thenReturn(Optional.of(antiga));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.editMessage(gestorPrincipal, conversationId, antiga.getId(), "texto novo"));
    }

    @Test
    void editMessageFuncionaParaOProprioAutorDentroDaJanela() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage minha = new ChatMessage(conv.getId(), gestorUserId, "texto original");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(minha.getId(), conv.getId())).thenReturn(Optional.of(minha));

        ChatMessageResponse resp = service.editMessage(gestorPrincipal, conversationId, minha.getId(), "texto corrigido");

        assertEquals("texto corrigido", resp.body());
        org.junit.jupiter.api.Assertions.assertNotNull(resp.editedAt());
    }

    @Test
    void deleteMessageEscondeOBodyMasMantemALinha() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage minha = new ChatMessage(conv.getId(), gestorUserId, "texto original");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(minha.getId(), conv.getId())).thenReturn(Optional.of(minha));

        ChatMessageResponse resp = service.deleteMessage(gestorPrincipal, conversationId, minha.getId());

        assertEquals(null, resp.body());
        org.junit.jupiter.api.Assertions.assertNotNull(resp.deletedAt());
    }

    @Test
    void editMessageRejeitaAposPrazoDe20Minutos() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage minha = new ChatMessage(conv.getId(), gestorUserId, "texto original");
        setField(minha, "sentAt", java.time.Instant.now().minus(21, java.time.temporal.ChronoUnit.MINUTES));
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(minha.getId(), conv.getId())).thenReturn(Optional.of(minha));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.editMessage(gestorPrincipal, conversationId, minha.getId(), "texto corrigido"));
    }

    @Test
    void deleteMessageFuncionaAos30MinutosMasRejeitaAos36() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage aos30 = new ChatMessage(conv.getId(), gestorUserId, "texto original");
        setField(aos30, "sentAt", java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.MINUTES));
        ChatMessage aos36 = new ChatMessage(conv.getId(), gestorUserId, "outro texto");
        setField(aos36, "sentAt", java.time.Instant.now().minus(36, java.time.temporal.ChronoUnit.MINUTES));
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(aos30.getId(), conv.getId())).thenReturn(Optional.of(aos30));
        when(messages.findByIdAndConversationId(aos36.getId(), conv.getId())).thenReturn(Optional.of(aos36));

        ChatMessageResponse resp = service.deleteMessage(gestorPrincipal, conversationId, aos30.getId());
        assertEquals(null, resp.body());

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.deleteMessage(gestorPrincipal, conversationId, aos36.getId()));
    }

    @Test
    void forwardMessageValidaParticipacaoNaConversaDeDestino() {
        UUID sourceConversationId = UUID.randomUUID();
        UUID targetConversationId = UUID.randomUUID();
        ChatConversation source = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage original = new ChatMessage(source.getId(), gestorUserId, "olha essa rota aqui");
        UUID outroGestor = UUID.randomUUID();
        ChatConversation targetDeOutraPessoa = new ChatConversation(tenantId, outroGestor, UUID.randomUUID(), null);
        when(conversations.findByIdAndTenantId(sourceConversationId, tenantId)).thenReturn(Optional.of(source));
        when(messages.findByIdAndConversationId(original.getId(), source.getId())).thenReturn(Optional.of(original));
        when(conversations.findByIdAndTenantId(targetConversationId, tenantId)).thenReturn(Optional.of(targetDeOutraPessoa));

        assertThrows(NotFoundException.class,
                () -> service.forwardMessage(gestorPrincipal, sourceConversationId, original.getId(), targetConversationId));
    }

    @Test
    void forwardMessageCriaMensagemNovaNoDestinoComMarcacao() {
        UUID sourceConversationId = UUID.randomUUID();
        UUID targetConversationId = UUID.randomUUID();
        Driver dOrigem = driverComLogin();
        Driver dDestino = driverComLogin();
        ChatConversation source = new ChatConversation(tenantId, gestorUserId, dOrigem.getId(), null);
        ChatMessage original = new ChatMessage(source.getId(), gestorUserId, "olha essa rota aqui");
        ChatConversation target = new ChatConversation(tenantId, gestorUserId, dDestino.getId(), null);
        when(conversations.findByIdAndTenantId(sourceConversationId, tenantId)).thenReturn(Optional.of(source));
        when(messages.findByIdAndConversationId(original.getId(), source.getId())).thenReturn(Optional.of(original));
        when(conversations.findByIdAndTenantId(targetConversationId, tenantId)).thenReturn(Optional.of(target));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(drivers.findById(dDestino.getId())).thenReturn(Optional.of(dDestino));

        ChatMessageResponse resp = service.forwardMessage(gestorPrincipal, sourceConversationId, original.getId(), targetConversationId);

        assertEquals("olha essa rota aqui", resp.body());
        assertEquals(original.getId(), resp.forwardedFromMessageId());
        assertEquals(target.getId(), resp.conversationId());
    }

    @Test
    void reactToMessageSubstituiReacaoAnteriorDaMesmaPessoa() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage msg = new ChatMessage(conv.getId(), gestorUserId, "oi");
        ChatMessageReaction anterior = new ChatMessageReaction(msg.getId(), gestorUserId, "👍");
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(msg.getId(), conv.getId())).thenReturn(Optional.of(msg));
        when(reactions.findByMessageIdAndUserId(msg.getId(), gestorUserId)).thenReturn(Optional.of(anterior));
        when(reactions.findAllByMessageIdIn(List.of(msg.getId())))
                .thenReturn(List.of(new ChatMessageReaction(msg.getId(), gestorUserId, "❤️")));

        List<ChatReactionResponse> result = service.reactToMessage(gestorPrincipal, conversationId, msg.getId(), "❤️");

        verify(reactions).delete(anterior);
        verify(reactions).save(any(ChatMessageReaction.class));
        assertEquals(1, result.size());
        assertEquals("❤️", result.get(0).emoji());
    }

    @Test
    void reactToMessageRejeitaForaDaJanelaDeRetencao() {
        UUID conversationId = UUID.randomUUID();
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, UUID.randomUUID(), null);
        ChatMessage antiga = new ChatMessage(conv.getId(), gestorUserId, "oi");
        antiga.removerDoServidor();
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.findByIdAndConversationId(antiga.getId(), conv.getId())).thenReturn(Optional.of(antiga));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.reactToMessage(gestorPrincipal, conversationId, antiga.getId(), "👍"));
    }

    // --- V33: chat em equipe ---

    @Test
    void getOrCreateTeamConversationRejeitaConversarConsigoMesmo() {
        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.getOrCreateTeamConversation(gestorPrincipal, gestorUserId));
    }

    @Test
    void getOrCreateTeamConversationRejeitaMotorista() {
        UUID motoristaUserId = UUID.randomUUID();
        com.autonomousapi.core.user.User motoristaUser =
                new com.autonomousapi.core.user.User(tenantId, "motorista@teste.local", "hash", com.autonomousapi.core.user.Role.MOTORISTA);
        setField(motoristaUser, "id", motoristaUserId);
        when(users.findByIdAndTenantId(motoristaUserId, tenantId)).thenReturn(Optional.of(motoristaUser));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.getOrCreateTeamConversation(gestorPrincipal, motoristaUserId));
    }

    @Test
    void getOrCreateTeamConversationOrdenaOParDeFormaCanonicaIndependenteDeQuemInicia() {
        UUID despachanteUserId = UUID.randomUUID();
        com.autonomousapi.core.user.User despachante = new com.autonomousapi.core.user.User(
                tenantId, "despachante@teste.local", "hash", com.autonomousapi.core.user.Role.DESPACHANTE);
        setField(despachante, "id", despachanteUserId);
        when(users.findByIdAndTenantId(despachanteUserId, tenantId)).thenReturn(Optional.of(despachante));
        when(users.findAllById(any())).thenReturn(List.of(despachante));
        when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID menor = gestorUserId.compareTo(despachanteUserId) <= 0 ? gestorUserId : despachanteUserId;
        UUID maior = gestorUserId.compareTo(despachanteUserId) <= 0 ? despachanteUserId : gestorUserId;
        when(conversations.findByTenantIdAndKindAndGestorUserIdAndParticipantBUserId(
                        tenantId, ChatConversationKind.EQUIPE, menor, maior))
                .thenReturn(Optional.empty());

        ChatConversationResponse resp = service.getOrCreateTeamConversation(gestorPrincipal, despachanteUserId);

        assertEquals("EQUIPE", resp.kind());
        assertEquals(despachanteUserId, resp.otherParticipantUserId());
        assertEquals("despachante@teste.local", resp.otherParticipantEmail());
    }

    @Test
    void getOrCreateTeamConversationEIdempotentePorPar() {
        UUID despachanteUserId = UUID.randomUUID();
        UUID menor = gestorUserId.compareTo(despachanteUserId) <= 0 ? gestorUserId : despachanteUserId;
        UUID maior = gestorUserId.compareTo(despachanteUserId) <= 0 ? despachanteUserId : gestorUserId;
        ChatConversation existente = ChatConversation.novaConversaEquipe(tenantId, menor, maior);
        com.autonomousapi.core.user.User despachante = new com.autonomousapi.core.user.User(
                tenantId, "despachante@teste.local", "hash", com.autonomousapi.core.user.Role.DESPACHANTE);
        setField(despachante, "id", despachanteUserId);
        when(users.findByIdAndTenantId(despachanteUserId, tenantId)).thenReturn(Optional.of(despachante));
        when(conversations.findByTenantIdAndKindAndGestorUserIdAndParticipantBUserId(
                        tenantId, ChatConversationKind.EQUIPE, menor, maior))
                .thenReturn(Optional.of(existente));

        ChatConversationResponse resp = service.getOrCreateTeamConversation(gestorPrincipal, despachanteUserId);

        assertEquals(existente.getId(), resp.id());
        verify(conversations, never()).save(any());
    }

    @Test
    void sendRoutePlanMessageRejeitaConversaDeEquipe() {
        UUID conversationId = UUID.randomUUID();
        UUID outroUserId = UUID.randomUUID();
        ChatConversation conv = ChatConversation.novaConversaEquipe(tenantId, gestorUserId, outroUserId);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));

        assertThrows(ChatMessageActionInvalidException.class,
                () -> service.sendRoutePlanMessage(gestorPrincipal, conversationId, UUID.randomUUID()));
    }

    @Test
    void sendMessageEmConversaDeEquipeNotificaOOutroParticipante() {
        UUID conversationId = UUID.randomUUID();
        UUID outroUserId = UUID.randomUUID();
        ChatConversation conv = ChatConversation.novaConversaEquipe(tenantId, gestorUserId, outroUserId);
        when(conversations.findByIdAndTenantId(conversationId, tenantId)).thenReturn(Optional.of(conv));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(gestorPrincipal, conversationId, "oi equipe", null);

        verify(pushNotificationService).notifyUser(eq(outroUserId), any(), any());
    }
}
