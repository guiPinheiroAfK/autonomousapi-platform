package com.autonomousapi.core.affiliate.dto;

import java.util.UUID;

/** vehicleId é opcional — clique pode vir de um contexto sem veículo específico (ex. tela geral de parceiros). */
public record AffiliateClickRequest(UUID vehicleId) {
}
