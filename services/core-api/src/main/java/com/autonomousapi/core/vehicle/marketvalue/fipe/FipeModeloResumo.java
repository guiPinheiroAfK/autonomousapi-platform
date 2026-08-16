package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeModeloResumo(int codigo, String nome) {}
