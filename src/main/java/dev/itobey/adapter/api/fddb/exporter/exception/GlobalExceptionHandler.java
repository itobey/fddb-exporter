package dev.itobey.adapter.api.fddb.exporter.exception;

import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.DateTimeException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns every exception that escapes a v2 REST controller into an RFC 7807 {@link ProblemDetail}.
 * <p>
 * One shape for every error, so a client parses {@code status}/{@code title}/{@code detail} once
 * instead of knowing a different key per exception. The same user mistake must also produce the
 * same status wherever it is detected: the "from after to" check is unified on
 * {@link DateTimeException} in both {@code FddbDataService} and {@code StatsService}, so
 * {@code /api/v2/fddbdata/range} and {@code /api/v2/stats/missing-days} answer identically.
 * <p>
 * The MCP layer does not go through here - {@code McpErrorAspect} words its own errors for a model.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://github.com/itobey/fddb-exporter/problems/";

    /**
     * Bean validation on a request body or query object. The per-field messages are kept, but under
     * a single {@code errors} property rather than at the root of the response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            fieldErrors.put(fieldName, error.getDefaultMessage());
        });
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields of the request are invalid", "validation-failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /**
     * A date or date range the caller got wrong - an inverted range, a range that is too long, a day
     * count outside the permitted window.
     */
    @ExceptionHandler(DateTimeException.class)
    public ProblemDetail handleDateTime(DateTimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Date Range", ex.getMessage(), "invalid-date-range");
    }

    /**
     * Any other rejected argument - an unknown metric, a limit out of bounds. The caller can fix it,
     * so it is a 400 exactly like the date cases above.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage(), "invalid-request");
    }

    /**
     * A query or path parameter Spring could not convert to the declared type - most often a date that
     * is not {@code YYYY-MM-DD}. Unlike Spring's own MVC exceptions this one is not an
     * {@link ErrorResponse}, so without this handler the catch-all below would turn a plain typo into
     * a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Parameter",
                "'" + ex.getValue() + "' is not a valid value for the parameter '" + ex.getName() + "'",
                "invalid-parameter");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request Body",
                "The request body is missing or could not be read as valid JSON", "invalid-request-body");
    }

    /**
     * 409 rather than 500: the request is fine and will work once the running export finishes, and
     * a client can decide for itself whether to wait or to give up.
     */
    @ExceptionHandler(ExportInProgressException.class)
    public ProblemDetail handleExportInProgress(ExportInProgressException ex) {
        return problem(HttpStatus.CONFLICT, "Export In Progress", ex.getMessage(), "export-in-progress");
    }

    /**
     * The diary HTML could not be parsed into anything usable, so the requested day has no data to
     * return. 404 rather than 500: nothing is broken on this side of the connection.
     */
    @ExceptionHandler(ParseException.class)
    public ProblemDetail handleParse(ParseException ex) {
        log.warn("Could not parse the fddb.info response", ex);
        return problem(HttpStatus.NOT_FOUND, "Data Not Available", ex.getMessage(), "data-not-available");
    }

    /**
     * A dependency this endpoint needs is not configured or not usable right now - most often
     * MongoDB being disabled. 503 rather than 500: the request was well-formed and would succeed
     * against a fully configured instance.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Endpoint unavailable in the current configuration: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), "service-unavailable");
    }

    /**
     * 502 rather than 500 or 503: logging into fddb.info failed, so the failure is an *upstream*
     * one. 500 would claim a bug in this application, and 503 would promise that waiting helps -
     * but rejected credentials do not fix themselves, they need the operator to correct the
     * configured username/password. 502 says exactly that: the gateway upstream refused us.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.error("Authentication against fddb.info failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Upstream Authentication Failed", ex.getMessage(),
                "upstream-authentication-failed");
    }

    /**
     * Catch-all so an unmapped exception still produces a ProblemDetail instead of Spring's default
     * error page. The stack trace stays in the log; the client gets a generic sentence, since an
     * internal message is of no use to it and may leak detail.
     * <p>
     * Spring's own MVC exceptions (unknown path, wrong method, missing parameter)
     * implement {@link ErrorResponse} and already carry the right status - they are passed through
     * with it rather than flattened to 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(errorResponse.updateAndGetBody(null, Locale.getDefault()));
        }
        if (ex instanceof TypeMismatchException || ex instanceof ServletException) {
            // Preserve Spring's handling of MVC failures that do not implement ErrorResponse.
            throw ex;
        }
        log.error("Unhandled exception while serving a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "The request could not be completed because of an internal error. "
                                + "The details were logged on the server.", "internal-error"));
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + type));
        return problem;
    }
}
