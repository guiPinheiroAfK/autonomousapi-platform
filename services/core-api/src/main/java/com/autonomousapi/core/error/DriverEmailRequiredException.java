package com.autonomousapi.core.error;

/** Tentativa de convidar um motorista sem e-mail cadastrado (ADR 0013). */
public class DriverEmailRequiredException extends RuntimeException {

    public DriverEmailRequiredException() {
        super("Cadastre um e-mail para o motorista antes de enviar o convite.");
    }
}
