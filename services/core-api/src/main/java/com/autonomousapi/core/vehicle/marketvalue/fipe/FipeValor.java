package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Só os dois campos que usamos — a resposta real tem mais (Marca, Modelo, MesReferencia...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeValor(@JsonProperty("Valor") String valor, @JsonProperty("CodigoFipe") String codigoFipe) {}
