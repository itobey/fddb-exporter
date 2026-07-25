package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two decisions that make the MCP server safe to ship: it is off unless asked for, and
 * its tools are only registered when the store they need is actually there.
 */
class McpToolRegistrationTest {

    private static final List<Class<?>> TOOL_CLASSES =
            List.of(FddbQueryTools.class, FddbStatsTools.class, FddbSchemaTools.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ToolTestConfiguration.class, FddbQueryTools.class, FddbStatsTools.class,
                    FddbSchemaTools.class);

    @Test
    void tools_shouldNotBeRegisteredByDefault() {
        contextRunner.run(context ->
                TOOL_CLASSES.forEach(toolClass -> assertThat(context).doesNotHaveBean(toolClass)));
    }

    @Test
    void tools_shouldNotBeRegisteredWithoutMongoDb() {
        contextRunner
                .withPropertyValues("fddb-exporter.mcp.enabled=true",
                        "fddb-exporter.persistence.mongodb.enabled=false")
                .run(context -> TOOL_CLASSES.forEach(toolClass -> assertThat(context).doesNotHaveBean(toolClass)));
    }

    @Test
    void tools_shouldBeRegisteredWhenMcpAndMongoDbAreEnabled() {
        contextRunner
                .withPropertyValues("fddb-exporter.mcp.enabled=true",
                        "fddb-exporter.persistence.mongodb.enabled=true")
                .run(context -> TOOL_CLASSES.forEach(toolClass -> assertThat(context).hasSingleBean(toolClass)));
    }

    @Test
    void tools_shouldAllBeNamedDescribedAndDeclaredReadOnly() {
        List<Method> tools = TOOL_CLASSES.stream()
                .flatMap(toolClass -> Arrays.stream(toolClass.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toList();

        assertThat(tools).hasSize(6);
        tools.forEach(method -> {
            McpTool tool = method.getAnnotation(McpTool.class);
            assertThat(tool.name()).as("name of %s", method).isNotBlank();
            assertThat(tool.description()).as("description of %s", method).isNotBlank();
            // destructiveHint defaults to true, so every read-only tool has to say so explicitly
            assertThat(tool.annotations().readOnlyHint()).as("readOnlyHint of %s", method).isTrue();
            assertThat(tool.annotations().destructiveHint()).as("destructiveHint of %s", method).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ToolTestConfiguration {

        @Bean
        FddbDataService fddbDataService() {
            return Mockito.mock(FddbDataService.class);
        }
    }
}
