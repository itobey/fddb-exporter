package dev.itobey.adapter.api.fddb.exporter.exception;

public class ParseException extends RuntimeException {

    // Parameterless Constructor
    public ParseException() {
    }

    // Constructor that accepts a message
    public ParseException(String message) {
        super(message);
    }

    // Constructor that accepts a message and the failure it originated from
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

}
