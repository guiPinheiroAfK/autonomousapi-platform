package com.autonomousapi.core.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** O dispositivo do gestor confirma que já persistiu localmente tudo até este instante (ADR 0015). */
public record SyncCursorRequest(@NotBlank String deviceId, @NotNull Instant syncedAt) {
}
