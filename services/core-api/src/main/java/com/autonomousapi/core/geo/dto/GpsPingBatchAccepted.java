package com.autonomousapi.core.geo.dto;

/** Espelha a resposta de geo-api POST /internal/v1/gps/pings/batch. */
public record GpsPingBatchAccepted(int accepted, int received) {
}
