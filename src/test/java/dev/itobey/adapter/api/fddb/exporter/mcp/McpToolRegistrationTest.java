package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
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

    private static final List<Class<?>> TOOL_CLASSES = List.of(FddbQueryTools.class, FddbStatsTools.class,
            FddbAnalysisTools.class, FddbCorrelationTools.class, FddbSchemaTools.class, FddbPrompts.class);

    /**
     * Every tool the server exposes. Asserted by name rather than by count so that adding a tool
     * without describing it here fails loudly instead of silently shipping.
     */
    private static final List<String> EXPECTED_TOOL_NAMES = List.of("get_day", "get_days", "search_products",
            "list_top_products", "get_stats", "get_averages", "get_extreme_days", "get_trend",
            "get_weekday_breakdown", "list_missing_days", "compare_periods", "check_goals",
            "correlate_products_with_dates", "get_data_schema");

    /**
     * Every prompt the server exposes, asserted by name for the same reason as the tools.
     */
    private static final List<String> EXPECTED_PROMPT_NAMES = List.of("weekly_nutrition_review",
            "find_trigger_foods", "protein_gap_analysis", "logging_hygiene_check");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ToolTestConfiguration.class, FddbQueryTools.class, FddbStatsTools.class,
                    FddbAnalysisTools.class, FddbCorrelationTools.class, FddbSchemaTools.class,
                    FddbPrompts.class);

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

        assertThat(tools).extracting(method -> method.getAnnotation(McpTool.class).name())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
        tools.forEach(method -> {
            McpTool tool = method.getAnnotation(McpTool.class);
            assertThat(tool.name()).as("name of %s", method).isNotBlank();
            assertThat(tool.description()).as("description of %s", method).isNotBlank();
            // destructiveHint defaults to true, so every read-only tool has to say so explicitly
            assertThat(tool.annotations().readOnlyHint()).as("readOnlyHint of %s", method).isTrue();
            assertThat(tool.annotations().destructiveHint()).as("destructiveHint of %s", method).isFalse();
        });
    }

    @Test
    void prompts_shouldAllBeNamedTitledAndDescribed() {
        List<Method> prompts = Arrays.stream(FddbPrompts.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpPrompt.class))
                .toList();

        assertThat(prompts).extracting(method -> method.getAnnotation(McpPrompt.class).name())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_PROMPT_NAMES);
        prompts.forEach(method -> {
            McpPrompt prompt = method.getAnnotation(McpPrompt.class);
            assertThat(prompt.title()).as("title of %s", method).isNotBlank();
            assertThat(prompt.description()).as("description of %s", method).isNotBlank();
            // without @McpArg an argument is advertised as "Parameter of type String", which tells
            // the user picking the prompt in their client nothing at all
            assertThat(method.getParameters()).as("arguments of %s", method)
                    .allSatisfy(parameter -> {
                        McpArg arg = parameter.getAnnotation(McpArg.class);
                        assertThat(arg).isNotNull();
                        assertThat(arg.name()).isNotBlank();
                        assertThat(arg.description()).isNotBlank();
                    });
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ToolTestConfiguration {

        @Bean
        FddbDataService fddbDataService() {
            return Mockito.mock(FddbDataService.class);
        }

        @Bean
        CorrelationService correlationService() {
            return Mockito.mock(CorrelationService.class);
        }
    }
}
