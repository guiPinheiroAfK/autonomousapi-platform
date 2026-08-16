package com.autonomousapi.core.error;

/** Gestor tentou enviar aviso a um motorista que ainda não aceitou o convite (ADR 0016). */
public class DriverWithoutLoginException extends RuntimeException {

    public DriverWithoutLoginException() {
        super("Este motorista ainda não tem acesso ao app — envie o convite primeiro.");
    }
}
