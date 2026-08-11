package com.autonomousapi.core.error;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("E-mail já cadastrado.");
    }
}
