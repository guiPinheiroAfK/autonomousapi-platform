package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeModelosResponse(List<FipeModeloResumo> modelos) {}
