package com.autonomousapi.core.passenger.notification;

/**
 * Abstração de envio de mensagem ao passageiro (spec 14) — desenhada pensando em migrar
 * pra WhatsApp assim que fizer sentido (Telegram é o canal ativo agora: gratuito, sem
 * aprovação de terceiro). Mesmo espírito de {@code EmailSender}/{@code PushSender}: funciona
 * sem credencial nenhuma (loga em vez de falhar), e vira real assim que houver um bot
 * configurado. Endereça por chat_id, não por telefone — Telegram só deixa mandar mensagem
 * pra quem já iniciou conversa com o bot (ver {@code Passenger.telegramChatId}).
 */
public interface PassengerNotificationSender {

    void sendMessage(long chatId, String text);
}
