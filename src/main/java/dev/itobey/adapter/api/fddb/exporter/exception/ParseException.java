package dev.itobey.adapter.api.fddb.exporter.exception;

public class ParseException extends RuntimeException {

    // Parameterless Constructor
    public ParseException() {
    }

    // Constructor that accepts a message
    public ParseException(String message) {
        super(message);
    }

}
