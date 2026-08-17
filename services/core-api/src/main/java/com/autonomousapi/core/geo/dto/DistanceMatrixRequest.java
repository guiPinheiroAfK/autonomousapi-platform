package com.autonomousapi.core.geo.dto;

import java.util.List;

/** Espelha o corpo esperado por geo-api POST /internal/v1/table (snake_case, Pydantic). */
public record DistanceMatrixRequest(List<Point> points) {

    public record Point(double lat, double lon) {
    }
}
