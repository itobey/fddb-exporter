package dev.itobey.adapter.api.fddb.exporter.exception;

import dev.itobey.adapter.api.fddb.exporter.rest.ManualExporterResource;

/**
 * This exception is thrown when handling of the manual export
 * from @{@link ManualExporterResource} fails.
 */
public class ManualExporterException extends Exception {

    // Parameterless Constructor
    public ManualExporterException() {
    }

    // Constructor that accepts a message
    public ManualExporterException(String message) {
        super(message);
    }

}
