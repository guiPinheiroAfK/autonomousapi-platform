package com.autonomousapi.core.passenger;

import com.autonomousapi.core.error.Lookups;
import com.autonomousapi.core.passenger.dto.PassengerRequest;
import com.autonomousapi.core.passenger.dto.PassengerResponse;
import com.autonomousapi.core.passenger.dto.TelegramLinkResponse;
import com.autonomousapi.core.passenger.notification.PassengerNotificationService;
import com.autonomousapi.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD de passageiros/clientes finais reutilizáveis (spec 14). Sem soft-delete (sem campo
 *  {@code ativo}) de propósito — é dado de terceiro sem consentimento direto (spec 14), e
 *  excluir de verdade é a disciplina de retenção certa aqui, não "marcar como inativo". */
@Service
public class PassengerService {

    private final PassengerRepository passengers;
    private final PassengerNotificationService notifications;
    private final String botUsername;

    public PassengerService(
            PassengerRepository passengers,
            PassengerNotificationService notifications,
            @Value("${app.telegram.bot-username:}") String botUsername) {
        this.passengers = passengers;
        this.notifications = notifications;
        this.botUsername = botUsername;
    }

    @Transactional
    public PassengerResponse create(JwtPrincipal gestorPrincipal, PassengerRequest req) {
        Passenger p = passengers.save(new Passenger(gestorPrincipal.tenantId(), req.nome(), req.telefone()));
        return PassengerResponse.from(p);
    }

    @Transactional(readOnly = true)
    public List<PassengerResponse> list(JwtPrincipal gestorPrincipal) {
        return passengers.findAllByTenantIdOrderByNomeAsc(gestorPrincipal.tenantId()).stream()
                .map(PassengerResponse::from)
                .toList();
    }

    @Transactional
    public PassengerResponse update(JwtPrincipal gestorPrincipal, UUID id, PassengerRequest req) {
        Passenger p = find(gestorPrincipal, id);
        p.atualizar(req.nome(), req.telefone());
        return PassengerResponse.from(p);
    }

    /** Exclusão real (não soft-delete) — route_stop.passenger_id é ON DELETE SET NULL
     *  (migration), então rotas passadas continuam íntegras, só perdem o vínculo. */
    @Transactional
    public void delete(JwtPrincipal gestorPrincipal, UUID id) {
        Passenger p = find(gestorPrincipal, id);
        passengers.delete(p);
    }

    /** Deep-link pra vincular o Telegram (spec 14) — o gestor manda esse link pro passageiro
     *  (WhatsApp, SMS, verbalmente) uma vez; passageiro clica, dá /start, e o webhook
     *  ({@code TelegramWebhookController}) grava o chat_id a partir do token. Sem bot
     *  configurado, devolve link vazio — a tela sabe que não tem o que mostrar. */
    @Transactional(readOnly = true)
    public TelegramLinkResponse telegramLink(JwtPrincipal gestorPrincipal, UUID id) {
        Passenger p = find(gestorPrincipal, id);
        return buildLinkResponse(p);
    }

    /** Gera um token novo — usado se o gestor quiser reenviar o link (ex. passageiro trocou
     *  de conta do Telegram, ou o link antigo se perdeu). Não desvincula um chat_id já
     *  confirmado; só o vínculo do webhook mais recente é que conta. */
    @Transactional
    public TelegramLinkResponse regenerateTelegramLink(JwtPrincipal gestorPrincipal, UUID id) {
        Passenger p = find(gestorPrincipal, id);
        p.gerarNovoTokenDeVinculo();
        return buildLinkResponse(p);
    }

    /** Chamado só pelo {@code TelegramWebhookController} — sem tenant no contexto (chamada
     *  pública, sem JWT), o token já escopa pra um passageiro só. */
    @Transactional
    public void vincularTelegram(String token, long chatId) {
        passengers.findByTelegramLinkToken(token).ifPresent(p -> {
            p.vincularTelegram(chatId);
            notifications.confirmarVinculo(p, p.getTenantId());
        });
    }

    private TelegramLinkResponse buildLinkResponse(Passenger p) {
        if (botUsername.isBlank()) {
            return new TelegramLinkResponse(null, p.temTelegramVinculado());
        }
        String url = "https://t.me/" + botUsername + "?start=" + p.getTelegramLinkToken();
        return new TelegramLinkResponse(url, p.temTelegramVinculado());
    }

    private Passenger find(JwtPrincipal gestorPrincipal, UUID id) {
        return Lookups.orNotFound(
                passengers.findByIdAndTenantId(id, gestorPrincipal.tenantId()), "Passageiro não encontrado.");
    }
}
