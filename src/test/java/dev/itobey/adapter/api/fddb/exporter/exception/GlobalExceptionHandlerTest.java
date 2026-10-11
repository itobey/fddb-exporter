package dev.itobey.adapter.api.fddb.exporter.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pins the status code and body shape of every exception {@link GlobalExceptionHandler} maps.
 * <p>
 * The point of the handler is that the <em>same</em> mistake always gets the <em>same</em> answer, which
 * is a property of the mapping table rather than of any one controller. So this slice drives a throwaway
 * controller that throws each type on demand, instead of hunting for a real endpoint that happens to
 * produce it - a new endpoint that throws one of these needs no new test here.
 * <p>
 * A {@code @WebMvcTest} slice on purpose: it boots no Mongo, no Influx and no Testcontainers, so it stays
 * in {@code mvn test}.
 */
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
@ActiveProfiles("test")
@Tag("v2")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dateTimeException_shouldBeBadRequestProblemDetail() throws Exception {
        mockMvc.perform(get("/test-errors/date-time"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Date Range"))
                .andExpect(jsonPath("$.detail").value("The 'from' date cannot be after the 'to' date"))
                .andExpect(jsonPath("$.type").value(containsString("invalid-date-range")));
    }

    @Test
    void illegalArgumentException_shouldBeBadRequest() throws Exception {
        mockMvc.perform(get("/test-errors/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(jsonPath("$.detail").value("limit must be positive"));
    }

    @Test
    void exportInProgressException_shouldBeConflict() throws Exception {
        mockMvc.perform(get("/test-errors/export-in-progress"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Export In Progress"))
                .andExpect(jsonPath("$.detail").value("An export is already running"));
    }

    @Test
    void parseException_shouldBeNotFound() throws Exception {
        mockMvc.perform(get("/test-errors/parse"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Data Not Available"))
                .andExpect(jsonPath("$.detail").value("Could not parse the diary page"));
    }

    @Test
    void illegalStateException_shouldBeServiceUnavailable() throws Exception {
        mockMvc.perform(get("/test-errors/illegal-state"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").value("MongoDB is not configured"));
    }

    /**
     * 502, not 500: the credentials fddb.info rejected are an upstream problem, and 500 would blame
     * this application for it.
     */
    @Test
    void authenticationException_shouldBeBadGateway() throws Exception {
        mockMvc.perform(get("/test-errors/authentication"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("Upstream Authentication Failed"))
                .andExpect(jsonPath("$.detail").value("Could not log in to fddb.info"));
    }

    /**
     * The catch-all must not hand the client the internal message - that is what the server log is for.
     */
    @Test
    void unmappedException_shouldBeGenericInternalServerError() throws Exception {
        mockMvc.perform(get("/test-errors/unmapped"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value(containsString("internal error")))
                .andExpect(jsonPath("$.detail").value(not(containsString("connection pool exhausted"))));
    }

    @Test
    void validationFailure_shouldListFieldErrorsUnderErrors() throws Exception {
        mockMvc.perform(post("/test-errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.name").value("name must not be null"));
    }

    /**
     * The catch-all must not swallow Spring's own MVC exceptions and report them as 500 - an unknown
     * path is still a 404.
     */
    @Test
    void unknownPath_shouldStayNotFoundRatherThanBecomeServerError() throws Exception {
        mockMvc.perform(get("/test-errors/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethod_shouldPreserveStatusAndAllowHeader() throws Exception {
        mockMvc.perform(post("/test-errors/date-time"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unreadableBody_shouldRemainBadRequest() throws Exception {
        mockMvc.perform(post("/test-errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Request Body"))
                .andExpect(jsonPath("$.detail").value(
                        "The request body is missing or could not be read as valid JSON"));
    }

    @RestController
    @RequestMapping("/test-errors")
    static class ThrowingController {

        @GetMapping("/date-time")
        void dateTime() {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("limit must be positive");
        }

        @GetMapping("/export-in-progress")
        void exportInProgress() {
            throw new ExportInProgressException("An export is already running");
        }

        @GetMapping("/parse")
        void parse() {
            throw new ParseException("Could not parse the diary page");
        }

        @GetMapping("/illegal-state")
        void illegalState() {
            throw new IllegalStateException("MongoDB is not configured");
        }

        @GetMapping("/authentication")
        void authentication() {
            throw new AuthenticationException("Could not log in to fddb.info");
        }

        @GetMapping("/unmapped")
        void unmapped() {
            throw new RuntimeException("connection pool exhausted");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload payload) {
            // never reached - the body is invalid in the test
        }

        static class Payload {
            @NotNull(message = "name must not be null")
            private String name;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
    }
}
