package com.autonomousapi.core.chat;

import com.autonomousapi.core.chat.dto.ChatConversationResponse;
import com.autonomousapi.core.chat.dto.ChatMessageResponse;
import com.autonomousapi.core.chat.dto.CreateConversationRequest;
import com.autonomousapi.core.chat.dto.SendMessageRequest;
import com.autonomousapi.core.chat.dto.SendRoutePlanRequest;
import com.autonomousapi.core.chat.dto.SyncCursorRequest;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mini-chat gestor↔motorista (spec 07, ADR 0015). Aberto a GESTOR_FROTA/ADMIN e MOTORISTA
 * — o isolamento por participante é feito no {@link ChatService}, não aqui, porque cada
 * papel enxerga conversas diferentes.
 */
@RestController
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Gestor-only: abre (ou recupera, se já existir) a conversa com um motorista. */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public ChatConversationResponse createConversation(
            @Valid @RequestBody CreateConversationRequest req, Authentication auth) {
        return chatService.getOrCreateConversation(principal(auth), req.driverId(), req.vehicleId());
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> listConversations(Authentication auth) {
        return chatService.listConversations(principal(auth));
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageResponse> listMessages(@PathVariable UUID id, Authentication auth) {
        return chatService.listMessages(principal(auth), id);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(
            @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req, Authentication auth) {
        return chatService.sendMessage(principal(auth), id, req.body());
    }

    /** Gestor-only: anexa uma rota já cadastrada à conversa (spec 07 item 8). */
    @PostMapping("/conversations/{id}/route-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public ChatMessageResponse sendRoutePlan(
            @PathVariable UUID id, @Valid @RequestBody SendRoutePlanRequest req, Authentication auth) {
        return chatService.sendRoutePlanMessage(principal(auth), id, req.routePlanId());
    }

    /** Gestor-only: cancela a rota pelo chat (ADR 0021) — único caminho que cancela rota já
     *  EM_ANDAMENTO; a tela de Rotas só cancela PLANEJADA. */
    @PostMapping("/conversations/{id}/route-plan/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public ChatMessageResponse cancelRoutePlan(
            @PathVariable UUID id, @Valid @RequestBody SendRoutePlanRequest req, Authentication auth) {
        return chatService.sendCancelamentoMessage(principal(auth), id, req.routePlanId());
    }

    /** Gestor-only: reatribui a rota ao motorista desta conversa (ADR 0021) — chamado na
     *  conversa do NOVO motorista, geralmente em resposta a uma SOLICITACAO_TROCA_MOTORISTA
     *  recebida na conversa do motorista atual. */
    @PostMapping("/conversations/{id}/route-plan/troca")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public ChatMessageResponse trocaMotorista(
            @PathVariable UUID id, @Valid @RequestBody SendRoutePlanRequest req, Authentication auth) {
        return chatService.sendTrocaMotoristaMessage(principal(auth), id, req.routePlanId());
    }

    /** Motorista-only: solicita cancelamento da rota ativa (ADR 0021) — nunca cancela
     *  sozinho, só avisa o gestor. */
    @PostMapping("/conversations/{id}/route-plan/solicitar-cancelamento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MOTORISTA')")
    public ChatMessageResponse solicitarCancelamento(@PathVariable UUID id, Authentication auth) {
        return chatService.solicitarCancelamento(principal(auth), id);
    }

    /** Motorista-only: solicita passar a rota ativa pra outra pessoa (ADR 0021). */
    @PostMapping("/conversations/{id}/route-plan/solicitar-troca")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MOTORISTA')")
    public ChatMessageResponse solicitarTroca(@PathVariable UUID id, Authentication auth) {
        return chatService.solicitarTrocaMotorista(principal(auth), id);
    }

    /** Marca como lidas as mensagens do outro participante ainda não lidas — chamado ao
     *  abrir/revisitar a conversa, por qualquer um dos dois lados. */
    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable UUID id, Authentication auth) {
        chatService.markAsRead(principal(auth), id);
    }

    /** Ping de "estou digitando" — efêmero (ver TypingIndicatorService), qualquer participante. */
    @PostMapping("/conversations/{id}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void typing(@PathVariable UUID id, Authentication auth) {
        chatService.registerTyping(principal(auth), id);
    }

    @GetMapping("/conversations/{id}/typing")
    public boolean isOtherTyping(@PathVariable UUID id, Authentication auth) {
        return chatService.isOtherParticipantTyping(principal(auth), id);
    }

    /** Gestor-only: confirma sincronização do device (habilita a limpeza no servidor). */
    @PostMapping("/sync-cursor")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR_FROTA', 'ADMIN')")
    public void syncCursor(@Valid @RequestBody SyncCursorRequest req, Authentication auth) {
        chatService.registerSyncCursor(principal(auth), req.deviceId(), req.syncedAt());
    }

    private JwtPrincipal principal(Authentication auth) {
        return (JwtPrincipal) auth.getPrincipal();
    }
}
