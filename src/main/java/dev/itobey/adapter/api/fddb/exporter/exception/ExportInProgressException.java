package dev.itobey.adapter.api.fddb.exporter.exception;

/**
 * Thrown when an export is requested while another one is still running.
 * <p>
 * Exporting means logging into fddb.info with the configured account and scraping it one day at a
 * time. Two runs at once double the outbound load on a third-party site under a single account and
 * interleave writes to the same days for no gain, so the second one is refused rather than queued -
 * a caller that is told "wait" can decide what to do, where a caller left hanging cannot.
 */
public class ExportInProgressException extends RuntimeException {

    public ExportInProgressException(String message) {
        super(message);
    }
}
