package com.autonomousapi.core.error;

public class TripStateConflictException extends RuntimeException {
    public TripStateConflictException(String message) {
        super(message);
    }
}
