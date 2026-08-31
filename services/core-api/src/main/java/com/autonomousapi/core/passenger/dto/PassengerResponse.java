package com.autonomousapi.core.passenger.dto;

import com.autonomousapi.core.passenger.Passenger;
import java.util.UUID;

public record PassengerResponse(UUID id, String nome, String telefone) {

    public static PassengerResponse from(Passenger p) {
        return new PassengerResponse(p.getId(), p.getNome(), p.getTelefone());
    }
}
