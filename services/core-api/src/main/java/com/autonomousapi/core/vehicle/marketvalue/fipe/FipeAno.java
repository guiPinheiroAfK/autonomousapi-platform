package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code codigo} vem no formato "2022-3" (ano-combustível) — ver FipeMatchingService#matchAno. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeAno(String codigo, String nome) {}
