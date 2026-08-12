package com.autonomousapi.core.trip.dto;

/**
 * Quantos pings do lote foram efetivamente aceitos. O app usa isso para descartar
 * exatamente esses da fila local e manter o resto para nova tentativa — por isso o
 * lote é processado em ordem e para no primeiro erro, em vez de falhar por inteiro.
 */
public record SubmitPingBatchResponse(int accepted, int received) {
}
