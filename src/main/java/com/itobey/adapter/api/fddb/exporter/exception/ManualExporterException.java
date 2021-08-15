package com.itobey.adapter.api.fddb.exporter.exception;

/**
 * This exception is thrown when handling of the manual export
 * from @{@link com.itobey.adapter.api.fddb.exporter.rest.ManualExporterResource} fails.
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
