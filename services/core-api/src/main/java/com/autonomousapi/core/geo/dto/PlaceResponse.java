package com.autonomousapi.core.geo.dto;

/** Resposta pública (camelCase, convenção do resto da API web) — ver GeoPlace. */
public record PlaceResponse(String displayName, double lat, double lon) {}
