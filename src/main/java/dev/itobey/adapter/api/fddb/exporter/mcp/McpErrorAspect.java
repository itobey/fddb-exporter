package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ExportInProgressException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.util.List;

/**
 * What the REST layer's {@code GlobalExceptionHandler} is for the API, for the MCP layer.
 * <p>
 * Spring AI turns whatever a tool, prompt or resource method throws into an error result carrying
 * the exception message, with no way to shape it in between. For the deliberate throws in this
 * package that is exactly right - they are written for the model that has to correct the call. For
 * everything else it means a Mongo connection failure, a Jackson error or an
 * {@code IllegalStateException} from deep in the service layer reaches the model as internal
 * implementation text, which it then relays to the user or, worse, tries to work around by calling
 * the tool again with different parameters.
 * <p>
 * So: known types through untouched, everything else replaced with one sentence that says the
 * problem is server-side and not worth retrying. The original is logged at ERROR with its stack
 * trace - the operator needs it, the model does not.
 * <p>
 * The replacement carries <em>no cause</em> on purpose. The MCP SDK builds its error text from the
 * root cause's message, so attaching the original would put it straight back into the response this
 * exists to keep it out of.
 */
@Aspect
@Component
@Slf4j
@ConditionalOnProperty(name = "fddb-exporter.mcp.enabled", havingValue = "true")
public class McpErrorAspect {

    /**
     * Exception types whose message is written for the client and passes through unchanged.
     * <p>
     * {@link DateTimeException} and {@link IllegalArgumentException} are how this package rejects a
     * bad parameter, and the whole point of those messages is that the model can fix the call from
     * them. {@link ExportInProgressException} is a "try again later" an agent handles well.
     * {@link AuthenticationException} names the one thing that would actually fix it - the stored
     * FDDB credentials - and carries none of them in the message.
     */
    private static final List<Class<? extends RuntimeException>> CLIENT_FACING = List.of(
            DateTimeException.class, IllegalArgumentException.class,
            ExportInProgressException.class, AuthenticationException.class);

    /**
     * Deliberately says both what happened and what not to do about it. A model told only that a
     * call failed will re-parameterise it and try again, burning turns on something no choice of
     * arguments can influence.
     */
    private static final String SERVER_SIDE_FAILURE = "The server could not complete this request - "
            + "this is a server-side problem, not something to retry with different parameters. "
            + "Tell the user the server failed and that the application log has the details.";

    @Around("@annotation(org.springframework.ai.mcp.annotation.McpTool) "
            + "|| @annotation(org.springframework.ai.mcp.annotation.McpPrompt) "
            + "|| @annotation(org.springframework.ai.mcp.annotation.McpResource)")
    public Object shapeErrors(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable thrown) {
            if (isClientFacing(thrown)) {
                throw thrown;
            }
            log.error("MCP: {} failed", joinPoint.getSignature().toShortString(), thrown);
            throw new IllegalStateException(SERVER_SIDE_FAILURE);
        }
    }

    private boolean isClientFacing(Throwable thrown) {
        return CLIENT_FACING.stream().anyMatch(type -> type.isInstance(thrown));
    }
}
