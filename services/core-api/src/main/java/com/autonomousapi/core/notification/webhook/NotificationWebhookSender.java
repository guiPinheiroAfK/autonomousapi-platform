package com.autonomousapi.core.notification.webhook;

/**
 * Notificação operacional interna (spec 12) — avisa a equipe, num canal de chat, sobre
 * eventos como signup/confirmação de conta. Não confundir com {@code EmailSender} (fala com
 * o cliente final) nem com {@code PassengerNotificationSender} (fala com o passageiro da
 * frota) — este canal fala com a própria equipe do produto.
 */
public interface NotificationWebhookSender {
    void notify(String message);
}
