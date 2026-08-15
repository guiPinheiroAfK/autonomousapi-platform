package com.autonomousapi.core.error;

/** Veículo já tem um motorista ativo designado (ADR 0014) — encerre a designação atual antes. */
public class VehicleAlreadyAssignedException extends RuntimeException {

    public VehicleAlreadyAssignedException() {
        super("Este veículo já está designado a outro motorista. Encerre a designação atual antes.");
    }
}
