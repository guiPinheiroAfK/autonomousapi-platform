package com.autonomousapi.core.error;

public class PlateAlreadyUsedException extends RuntimeException {
    public PlateAlreadyUsedException() {
        super("Já existe um veículo com essa placa nesta frota.");
    }
}
