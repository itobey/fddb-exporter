package dev.itobey.adapter.api.fddb.exporter.exception;

import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.DateTimeException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class, DateTimeException.class})
    public Map<String, String> handleValidationExceptions(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            methodArgumentNotValidException.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
        }
        if (ex instanceof DateTimeException dateTimeException) {
            errors.put("dateTimeError", dateTimeException.getMessage());
        }
        return errors;
    }

    /**
     * 409 rather than 500: the request is fine and will work once the running export finishes, and
     * a client can decide for itself whether to wait or to give up.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler({ExportInProgressException.class})
    public Map<String, String> handleExportInProgress(ExportInProgressException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("exportError", ex.getMessage());
        return errors;
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler({AuthenticationException.class})
    public Map<String, String> handleAuthenticationExceptions(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        if (ex instanceof AuthenticationException authenticationException) {
            errors.put("authenticationError", authenticationException.getMessage());
        }
        return errors;
    }

    /**
     * Last resort for everything else, so that no failure leaves the application without a trace.
     * <p>
     * Spring's default error handling neither logs the exception nor puts anything about it in the
     * response, so an unchecked failure deep in the scraper - a page that no longer parses, say -
     * surfaced as an empty HTTP 500 with an empty log next to it, and the Web UI could only report
     * that "something" went wrong.
     *
     * @param ex the exception no other handler claimed
     * @return HTTP 500 naming the failure
     * @throws Exception the very same exception, for the ones Spring maps itself
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedExceptions(Exception ex) throws Exception {
        if (ex instanceof ErrorResponse || ex instanceof TypeMismatchException || ex instanceof ServletException) {
            // a bad request parameter, an unknown path and the like already have a status of their
            // own. Rethrowing this very instance makes Spring fall through to the resolvers that
            // know them, rather than turning all of them into 500s here.
            throw ex;
        }
        log.error("unexpected error while handling a request", ex);
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errors);
    }
}
