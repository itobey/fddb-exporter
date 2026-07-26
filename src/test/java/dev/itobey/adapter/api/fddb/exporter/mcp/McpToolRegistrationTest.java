package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.VersionCheckService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the three decisions that make the MCP server safe to ship: it is off unless asked for, its
 * tools are only registered when the store they need is actually there, and the tools that write are
 * only registered when writing was asked for separately.
 */
class McpToolRegistrationTest {

    private static final List<Class<?>> TOOL_CLASSES = List.of(FddbQueryTools.class, FddbStatsTools.class,
            FddbAnalysisTools.class, FddbCorrelationTools.class, FddbSchemaTools.class,
            FddbServerInfoTools.class, FddbResources.class, FddbPrompts.class);

    /**
     * Every read-only tool the server exposes. Asserted by name rather than by count so that adding a
     * tool without describing it here fails loudly instead of silently shipping.
     */
    private static final List<String> EXPECTED_TOOL_NAMES = List.of("get_day", "get_days", "search_products",
            "list_top_products", "get_product_summary", "list_distinct_products", "find_days_with_products",
            "get_stats", "get_averages", "get_extreme_days", "get_trend", "get_weekday_breakdown",
            "get_macro_split", "list_missing_days", "compare_periods", "check_goals",
            "correlate_products_with_dates", "get_data_schema", "get_server_info");

    /**
     * The tools that scrape fddb.info and write, kept separate from the list above because their
     * annotations have to say the opposite of every other tool's.
     */
    private static final List<String> EXPECTED_WRITE_TOOL_NAMES =
            List.of("export_range", "export_days_back", "export_missing_days");

    /**
     * Every prompt the server exposes, asserted by name for the same reason as the tools.
     */
    private static final List<String> EXPECTED_PROMPT_NAMES = List.of("weekly_nutrition_review",
            "find_trigger_foods", "protein_gap_analysis", "logging_hygiene_check");

    /**
     * Every resource the server exposes, likewise.
     */
    private static final List<String> EXPECTED_RESOURCE_URIS =
            List.of("fddb://stats", "fddb://day/{date}", "fddb://schema");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ToolTestConfiguration.class, FddbQueryTools.class, FddbStatsTools.class,
                    FddbAnalysisTools.class, FddbCorrelationTools.class, FddbSchemaTools.class,
                    FddbServerInfoTools.class, FddbResources.class, FddbPrompts.class, FddbExportTools.class);

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
    void writeTools_shouldNotBeRegisteredAlongsideTheReadOnlyOnes() {
        contextRunner
                .withPropertyValues("fddb-exporter.mcp.enabled=true",
                        "fddb-exporter.persistence.mongodb.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FddbExportTools.class));
    }

    @Test
    void writeTools_shouldNeedTheirOwnFlagOnTopOfTheOtherTwo() {
        contextRunner
                .withPropertyValues("fddb-exporter.mcp.write-tools-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FddbExportTools.class));

        contextRunner
                .withPropertyValues("fddb-exporter.mcp.enabled=true",
                        "fddb-exporter.persistence.mongodb.enabled=false",
                        "fddb-exporter.mcp.write-tools-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(FddbExportTools.class));
    }

    @Test
    void writeTools_shouldBeRegisteredWhenAllThreeFlagsAreSet() {
        contextRunner
                .withPropertyValues("fddb-exporter.mcp.enabled=true",
                        "fddb-exporter.persistence.mongodb.enabled=true",
                        "fddb-exporter.mcp.write-tools-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(FddbExportTools.class));
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
    void writeTools_shouldDeclareThatTheyWriteAndReachTheInternet() {
        List<Method> writeTools = Arrays.stream(FddbExportTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toList();

        assertThat(writeTools).extracting(method -> method.getAnnotation(McpTool.class).name())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_WRITE_TOOL_NAMES);
        writeTools.forEach(method -> {
            McpTool tool = method.getAnnotation(McpTool.class);
            assertThat(tool.description()).as("description of %s", method).isNotBlank();
            assertThat(tool.annotations().readOnlyHint()).as("readOnlyHint of %s", method).isFalse();
            // they upsert, they never delete
            assertThat(tool.annotations().destructiveHint()).as("destructiveHint of %s", method).isFalse();
            assertThat(tool.annotations().idempotentHint()).as("idempotentHint of %s", method).isTrue();
            // they call out to fddb.info, unlike every read tool
            assertThat(tool.annotations().openWorldHint()).as("openWorldHint of %s", method).isTrue();
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

    @Test
    void resources_shouldAllBeNamedDescribedAndTyped() {
        List<Method> resources = Arrays.stream(FddbResources.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpResource.class))
                .toList();

        assertThat(resources).extracting(method -> method.getAnnotation(McpResource.class).uri())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_RESOURCE_URIS);
        resources.forEach(method -> {
            McpResource resource = method.getAnnotation(McpResource.class);
            assertThat(resource.name()).as("name of %s", method).isNotBlank();
            assertThat(resource.title()).as("title of %s", method).isNotBlank();
            assertThat(resource.description()).as("description of %s", method).isNotBlank();
            assertThat(resource.mimeType()).as("mimeType of %s", method).isNotBlank();
            // a resource method may only return a String or an SDK content type
            assertThat(method.getReturnType()).as("return type of %s", method).isEqualTo(String.class);
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

        @Bean
        VersionCheckService versionCheckService() {
            return Mockito.mock(VersionCheckService.class);
        }

        @Bean
        FddbExporterProperties fddbExporterProperties() {
            return Mockito.mock(FddbExporterProperties.class);
        }
    }
}
