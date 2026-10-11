package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

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

    /**
     * Builds the message a Vaadin view shows the user out of an RFC 7807 error response.
     * <p>
     * Every v2 endpoint answers errors with a {@link ProblemDetail} (see {@code GlobalExceptionHandler}),
     * whose {@code detail} is a sentence written for a human - "The 'from' date cannot be after the 'to'
     * date". Without this, the view would show {@code RestClientException.getMessage()}, i.e. the status
     * line with the raw JSON body appended, which is not something to put in a notification.
     *
     * @param prefix what the UI was trying to do, e.g. "Failed to export for date range"
     * @param cause  the failure from {@link org.springframework.web.client.RestTemplate}
     * @return an ApiException carrying the server's own wording where there is one
     */
    static ApiException from(String prefix, RestClientException cause) {
        return new ApiException(prefix + ": " + describe(cause), cause);
    }

    private static String describe(RestClientException cause) {
        if (cause instanceof HttpStatusCodeException statusCodeException) {
            try {
                ProblemDetail problem = statusCodeException.getResponseBodyAs(ProblemDetail.class);
                if (problem != null && problem.getDetail() != null && !problem.getDetail().isBlank()) {
                    return problem.getDetail();
                }
            } catch (RuntimeException ignored) {
                // not a problem+json body - fall through to the status line below
            }
            return statusCodeException.getStatusCode() + " " + statusCodeException.getStatusText();
        }
        return cause.getMessage();
    }
}
