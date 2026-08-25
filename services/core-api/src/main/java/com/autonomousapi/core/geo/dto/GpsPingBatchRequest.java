package com.autonomousapi.core.geo.dto;

import java.util.List;

/** Espelha o corpo esperado por geo-api POST /internal/v1/gps/pings/batch (Pydantic). */
public record GpsPingBatchRequest(List<GpsPingRequest> pings) {
}
