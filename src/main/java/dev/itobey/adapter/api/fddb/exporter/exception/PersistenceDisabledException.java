package dev.itobey.adapter.api.fddb.exporter.exception;

/**
 * A required store is disabled. The global handler maps this to HTTP 503 with a ProblemDetail body.
 */
public class PersistenceDisabledException extends IllegalStateException {

    public PersistenceDisabledException(String message) {
        super(message);
    }
}
