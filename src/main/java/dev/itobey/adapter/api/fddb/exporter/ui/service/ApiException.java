package dev.itobey.adapter.api.fddb.exporter.ui.service;

/**
 * Exception thrown when an API call fails.
 */
public class ApiException extends Exception {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

