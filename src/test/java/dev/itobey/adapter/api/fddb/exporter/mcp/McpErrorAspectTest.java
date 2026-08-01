package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ExportInProgressException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.DateTimeException;

import static org.junit.jupiter.api.Assertions.*;

class McpErrorAspectTest {

    @Test
    void shouldLetADeliberateParameterRejectionThroughUntouched() {
        DateTimeException thrown = new DateTimeException("'january' is not a valid date - pass an ISO date");
        Tools tools = proxy(new Tools(thrown));

        DateTimeException caught = assertThrows(DateTimeException.class, tools::someTool);

        // the whole value of these messages is that the model can fix the call from them
        assertSame(thrown, caught);
    }

    @Test
    void shouldLetADeliberateArgumentRejectionThroughUntouched() {
        IllegalArgumentException thrown =
                new IllegalArgumentException("At least one include keyword is required");
        Tools tools = proxy(new Tools(thrown));

        assertSame(thrown, assertThrows(IllegalArgumentException.class, tools::someTool));
    }

    @Test
    void shouldNotSwallowTheExportInProgressRefusal() {
        // "try again later" is something an agent handles well - replacing it would make it retry blind
        ExportInProgressException thrown = new ExportInProgressException("An export is already running");
        Tools tools = proxy(new Tools(thrown));

        assertSame(thrown, assertThrows(ExportInProgressException.class, tools::someTool));
    }

    @Test
    void shouldNotSwallowTheFddbCredentialFailure() {
        // it names the one thing that would fix this, and carries no credentials
        AuthenticationException thrown =
                new AuthenticationException("Login to FDDB not successful, please check credentials");
        Tools tools = proxy(new Tools(thrown));

        assertSame(thrown, assertThrows(AuthenticationException.class, tools::someTool));
    }

    @Test
    void shouldReplaceAnInternalFailureWithSomethingTheModelCanActOn() {
        Tools tools = proxy(new Tools(new IllegalStateException("MongoDB is not configured")));

        IllegalStateException caught = assertThrows(IllegalStateException.class, tools::someTool);

        assertTrue(caught.getMessage().contains("server-side problem"), caught.getMessage());
        assertTrue(caught.getMessage().contains("not something to retry with different parameters"),
                caught.getMessage());
        assertFalse(caught.getMessage().contains("MongoDB is not configured"), caught.getMessage());
        // the SDK builds its error text from the root cause's message, so attaching the original
        // would put it straight back into the response this exists to keep it out of
        assertNull(caught.getCause());
    }

    @Test
    void shouldCoverPromptsAndResourcesAsWellAsTools() {
        Tools tools = proxy(new Tools(new NullPointerException("Cannot invoke \"Product.getName()\"")));

        assertTrue(assertThrows(IllegalStateException.class, tools::somePrompt)
                .getMessage().contains("server-side problem"));
        assertTrue(assertThrows(IllegalStateException.class, tools::someResource)
                .getMessage().contains("server-side problem"));
    }

    @Test
    void shouldLeaveAMethodThatIsNoneOfThoseThingsAlone() {
        // the pointcut is the annotations, not the class - a helper must not be rewritten
        Tools tools = proxy(new Tools(new IllegalStateException("MongoDB is not configured")));

        assertEquals("MongoDB is not configured",
                assertThrows(IllegalStateException.class, tools::notAnnotated).getMessage());
    }

    @Test
    void shouldReturnTheResultWhenNothingFails() {
        assertEquals("ok", proxy(new Tools(null)).someTool());
    }

    private Tools proxy(Tools target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new McpErrorAspect());
        return factory.getProxy();
    }

    /**
     * Stands in for the real tool classes: the aspect keys off the annotations, so anything carrying
     * them exercises the same pointcut.
     */
    static class Tools {

        private final RuntimeException failure;

        Tools(RuntimeException failure) {
            this.failure = failure;
        }

        @McpTool(name = "some_tool", description = "irrelevant")
        public String someTool() {
            return run();
        }

        @McpPrompt(name = "some_prompt", description = "irrelevant")
        public String somePrompt() {
            return run();
        }

        @McpResource(uri = "test://thing", name = "some_resource", description = "irrelevant")
        public String someResource() {
            return run();
        }

        public String notAnnotated() {
            return run();
        }

        private String run() {
            if (failure != null) {
                throw failure;
            }
            return "ok";
        }
    }
}
