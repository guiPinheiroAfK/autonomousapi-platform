package com.autonomousapi.core.passenger.dto;

/** {@code linkUrl} nulo quando não há bot configurado (dev/demo) — a tela sabe que não tem
 *  o que mostrar em vez de exibir um link quebrado. {@code vinculado} diz se o passageiro
 *  já deu /start (independe de gerar um link novo ou não). */
public record TelegramLinkResponse(String linkUrl, boolean vinculado) {
}
