package dev.itobey.adapter.api.fddb.exporter.exception;

public class AuthenticationException extends RuntimeException {

    // Parameterless Constructor
    public AuthenticationException() {
    }

    // Constructor that accepts a message
    public AuthenticationException(String message) {
        super(message);
    }

}
