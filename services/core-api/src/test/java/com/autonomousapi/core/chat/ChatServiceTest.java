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
import com.autonomousapi.core.driver.CurrentDriverResolver;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
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
import com.autonomousapi.core.vehicle.VehicleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatSyncCursorRepository syncCursors = mock(ChatSyncCursorRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final CurrentDriverResolver driverResolver = mock(CurrentDriverResolver.class);
    private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);
    private final RoutePlanService routePlanService = mock(RoutePlanService.class);

    private final ChatService service = new ChatService(
            conversations, messages, syncCursors, drivers, vehicles, tenants, driverResolver,
            pushNotificationService, routePlanService);

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

        service.sendMessage(gestorPrincipal, conversationId, "Oi, tudo bem?");

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

        service.sendMessage(motoristaPrincipal, conversationId, "Cheguei na oficina");

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
        ChatConversation conv = new ChatConversation(tenantId, gestorUserId, driverId, null);
        Driver d = driverComLogin();
        Tenant tenant = new Tenant("Frota Rota Certa");
        JwtPrincipal motoristaPrincipal = new JwtPrincipal(UUID.randomUUID(), tenantId, "MOTORISTA");
        when(driverResolver.resolve(motoristaPrincipal)).thenReturn(d);
        when(conversations.findAllByDriverIdOrderByCreatedAtDesc(d.getId())).thenReturn(List.of(conv));
        when(drivers.findById(driverId)).thenReturn(Optional.of(d));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
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
        when(routePlanService.assignDriver(gestorPrincipal, routePlanId, d.getId()))
                .thenReturn(new RoutePlanResponse(routePlanId, d.getId(), d.getName(), null, null,
                        RoutePlanStatus.PLANEJADA, java.time.Instant.now(), List.of()));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(drivers.findById(d.getId())).thenReturn(Optional.of(d));

        ChatMessageResponse resp = service.sendRoutePlanMessage(gestorPrincipal, conversationId, routePlanId);

        assertEquals(ChatMessageType.ROUTE_ASSIGNMENT, resp.messageType());
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
        when(routePlanService.assignDriver(gestorPrincipal, routePlanId, d.getId()))
                .thenThrow(new RoutePlanAlreadyAssignedException());

        assertThrows(RoutePlanAlreadyAssignedException.class,
                () -> service.sendRoutePlanMessage(gestorPrincipal, conversationId, routePlanId));
        verify(messages, never()).save(any());
    }
}
