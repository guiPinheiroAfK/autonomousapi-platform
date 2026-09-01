package com.autonomousapi.core.passenger.notification;

import com.autonomousapi.core.passenger.Passenger;
import com.autonomousapi.core.passenger.PassengerRepository;
import com.autonomousapi.core.routeplan.RoutePlan;
import com.autonomousapi.core.routeplan.RouteStop;
import com.autonomousapi.core.routeplan.RouteStopRepository;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de {@code route_plan_event} (spec 14, parte 2) — não é lógica paralela ao
 * trâmite de rota, é mais um efeito colateral das mesmas transições que {@code RoutePlanService}
 * já grava. Toda mensagem identifica a empresa remetente (spec 14: dado de terceiro sem
 * consentimento direto, passageiro nunca interagiu com o sistema antes). Falha de envio
 * nunca propaga — mesma regra da spec 12, reforçada em {@link PassengerNotificationSender}.
 */
@Service
public class PassengerNotificationService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PassengerRepository passengers;
    private final RouteStopRepository routeStops;
    private final TenantRepository tenants;
    private final PassengerNotificationSender sender;

    public PassengerNotificationService(
            PassengerRepository passengers,
            RouteStopRepository routeStops,
            TenantRepository tenants,
            PassengerNotificationSender sender) {
        this.passengers = passengers;
        this.routeStops = routeStops;
        this.tenants = tenants;
        this.sender = sender;
    }

    /** Evento ATRIBUIDA — "sua viagem está confirmada", pra todos os passageiros de
     *  qualquer parada da rota. Devolve {@code true} se mandou pra pelo menos um passageiro
     *  vinculado (RoutePlanService usa isso pra marcar {@code passageirosNotificados}, que
     *  decide se um cancelamento futuro precisa avisar de volta). */
    @Transactional(readOnly = true)
    public boolean notificarConfirmacao(RoutePlan plan) {
        List<Passenger> passageiros = passageirosDaRota(plan.getId());
        if (passageiros.isEmpty()) return false;
        String empresa = nomeDaEmpresa(plan.getTenantId());
        String texto = empresa + ": sua viagem está confirmada para " + plan.getDataExecucao().format(DATA_BR) + ".";
        return passageiros.stream().map(p -> enviar(p, texto)).reduce(false, Boolean::logicalOr);
    }

    /** Primeira parada concluída da rota (PLANEJADA→EM_ANDAMENTO) — "motorista a caminho",
     *  pra todos os passageiros da rota, não só o da parada que disparou a transição. */
    @Transactional(readOnly = true)
    public void notificarACaminho(RoutePlan plan) {
        String empresa = nomeDaEmpresa(plan.getTenantId());
        String texto = empresa + ": o motorista está a caminho.";
        passageirosDaRota(plan.getId()).forEach(p -> enviar(p, texto));
    }

    /** Parada concluída — só o passageiro daquela parada específica (útil pra ida/volta,
     *  spec 13: o mesmo aviso sai de novo na volta, cada perna com sua própria parada). */
    @Transactional(readOnly = true)
    public void notificarEmbarqueConfirmado(RoutePlan plan, RouteStop stop) {
        if (stop.getPassengerId() == null) return;
        passengers.findById(stop.getPassengerId()).ifPresent(p -> {
            String empresa = nomeDaEmpresa(plan.getTenantId());
            enviar(p, empresa + ": embarque confirmado. Boa viagem!");
        });
    }

    /** Cancelamento — só dispara se {@code passageirosNotificados} (não deixar a última
     *  mensagem recebida ser "confirmado" quando a viagem não está mais de pé). */
    @Transactional(readOnly = true)
    public void notificarCancelamento(RoutePlan plan) {
        if (!plan.isPassageirosNotificados()) return;
        String empresa = nomeDaEmpresa(plan.getTenantId());
        String texto = empresa + ": sua viagem foi cancelada.";
        passageirosDaRota(plan.getId()).forEach(p -> enviar(p, texto));
    }

    /** Botão "Avisar passageiro" (disparo manual, sob demanda) — mesmo texto-base do evento
     *  correspondente ao estado atual da parada (spec 14). */
    @Transactional(readOnly = true)
    public void notificarManualmente(RoutePlan plan, RouteStop stop) {
        if (stop.getPassengerId() == null) return;
        passengers.findById(stop.getPassengerId()).ifPresent(p -> {
            String empresa = nomeDaEmpresa(plan.getTenantId());
            String texto = stop.isConcluida()
                    ? empresa + ": embarque confirmado. Boa viagem!"
                    : empresa + ": o motorista está a caminho.";
            enviar(p, texto);
        });
    }

    /** Reply do próprio webhook de vínculo (spec 14) — é a primeira interação de verdade,
     *  identifica a empresa desde já. */
    public void confirmarVinculo(Passenger passenger, UUID tenantId) {
        String empresa = nomeDaEmpresa(tenantId);
        sender.sendMessage(
                passenger.getTelegramChatId(),
                "Você está vinculado à " + empresa + "! A partir de agora, avisos da sua viagem chegam por aqui.");
    }

    /** {@code false} se o passageiro ainda não deu /start no bot — silencioso de propósito,
     *  não é erro, é só "ainda não vinculou". */
    private boolean enviar(Passenger p, String texto) {
        if (!p.temTelegramVinculado()) return false;
        sender.sendMessage(p.getTelegramChatId(), texto);
        return true;
    }

    private List<Passenger> passageirosDaRota(UUID routePlanId) {
        List<UUID> ids = routeStops.findAllByRoutePlanIdOrderByOrdemSugeridaAsc(routePlanId).stream()
                .map(RouteStop::getPassengerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return List.of();
        return passengers.findAllById(ids);
    }

    private String nomeDaEmpresa(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName).orElse("sua frota");
    }
}
