package com.autonomousapi.core.error;

public class CnhAlreadyUsedException extends RuntimeException {
    public CnhAlreadyUsedException() {
        super("Já existe um motorista com essa CNH nesta frota.");
    }
}
