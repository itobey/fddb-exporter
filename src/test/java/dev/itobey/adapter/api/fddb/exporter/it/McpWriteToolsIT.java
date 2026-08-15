package dev.itobey.adapter.api.fddb.exporter.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.itobey.adapter.api.fddb.exporter.config.TestConfig;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the export tools over HTTP with the real MCP SDK client, against a WireMock standing in for
 * fddb.info.
 * <p>
 * A separate context from {@code McpServerIT} on purpose: the flag that registers these
 * tools is a bean condition, so the two states cannot coexist in one application context - which is
 * exactly the property worth proving. The read-only test asserts the export tools are absent; this
 * one asserts that with the flag set they appear, scrape and persist.
 * <p>
 * A second Spring context plus a MongoDB container is real build time, but the only tools in this
 * application that write to a third-party account under the user's credentials should not be the
 * ones covered by a suite someone has to remember to run - as an {@code *IT} it is part of every
 * {@code mvn verify}, not something invoked by name.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fddb-exporter.mcp.enabled=true",
        "fddb-exporter.mcp.write-tools-enabled=true",
        "fddb-exporter.persistence.influxdb.enabled=false"
})
@Testcontainers
@EnableFeignClients
@ActiveProfiles("test")
@Import(TestConfig.class)
class McpWriteToolsIT {

    private static final LocalDate DAY_WITH_DATA = LocalDate.of(2024, 8, 27);
    private static final LocalDate DAY_WITHOUT_DATA = LocalDate.of(2024, 8, 28);

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.9");

    @LocalServerPort
    private int port;

    @Autowired
    private FddbDataRepository fddbDataRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private McpSyncClient mcpClient;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("fddb-exporter.fddb.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        // deletes the documents rather than the collection: dropping it would take the declared indexes
        // with it, and the unique index on date is part of what these tests exercise
        mongoTemplate.remove(new Query(), FddbData.class);
        // the WireMock server is static, so its request journal outlives a single test
        wireMockServer.resetRequests();
        stubFddb(DAY_WITH_DATA, "data-available-2024-08-27.html");
        stubFddb(DAY_WITHOUT_DATA, "no-data-2024-08-28.html");

        mcpClient = McpClient
                .sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        mcpClient.initialize();
    }

    @AfterEach
    void tearDown() {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    @Test
    void listTools_shouldExposeTheExportToolsAsWritingAndReachingTheInternet() {
        McpSchema.ListToolsResult tools = mcpClient.listTools();

        assertThat(tools.tools()).extracting(McpSchema.Tool::name)
                .contains("export_range", "export_days_back", "export_missing_days");
        assertThat(tools.tools())
                .filteredOn(tool -> tool.name().startsWith("export_"))
                .allSatisfy(tool -> {
                    assertThat(tool.annotations().readOnlyHint()).isFalse();
                    assertThat(tool.annotations().destructiveHint()).isFalse();
                    assertThat(tool.annotations().openWorldHint()).isTrue();
                });
    }

    @Test
    void exportRange_shouldScrapeTheRangeAndPersistIt() {
        String result = callTool("export_range",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITHOUT_DATA.toString()));

        assertThat(result).contains("\"daysRequested\":2", "\"successCount\":1", "\"failureCount\":1",
                "\"successfulDays\":[\"2024-08-27\"]", "\"unsuccessfulDays\":[\"2024-08-28\"]",
                // a day FDDB has nothing for is not an error, and the message has to say so
                "nothing was logged");
        assertThat(fddbDataRepository.findFirstByDate(DAY_WITH_DATA)).isPresent()
                .get()
                .satisfies(entry -> assertThat(entry.getTotalCalories()).isEqualTo(2128.0));
        assertThat(fddbDataRepository.findFirstByDate(DAY_WITHOUT_DATA)).isEmpty();
    }

    @Test
    void exportRange_shouldUpdateAnExistingDayRatherThanDuplicateIt() {
        callTool("export_range",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITH_DATA.toString()));
        callTool("export_range",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITH_DATA.toString()));

        assertThat(fddbDataRepository.findAll()).hasSize(1);
    }

    @Test
    void exportMissingDays_shouldOnlyFetchTheGaps() {
        callTool("export_range",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITH_DATA.toString()));
        wireMockServer.resetRequests();

        String result = callTool("export_missing_days",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITHOUT_DATA.toString()));

        // the already logged day is neither re-fetched nor reported
        assertThat(result).contains("\"daysRequested\":1", "\"successCount\":0", "\"failureCount\":1");
        verify(1, getRequestedFor(urlPathEqualTo("/db/i18n/myday20/")));
    }

    @Test
    void exportMissingDays_shouldReportThatNothingWasMissingWithoutScraping() {
        callTool("export_range",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITH_DATA.toString()));
        wireMockServer.resetRequests();

        String result = callTool("export_missing_days",
                Map.of("fromDate", DAY_WITH_DATA.toString(), "toDate", DAY_WITH_DATA.toString()));

        assertThat(result).contains("already has an entry");
        verify(0, getRequestedFor(urlPathEqualTo("/db/i18n/myday20/")));
    }

    @Test
    void exportRange_shouldRefuseATooLongRangeAsAToolErrorWithoutScraping() {
        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest("export_range",
                Map.of("fromDate", "2024-01-01", "toDate", "2024-12-31")));

        assertThat(result.isError()).isTrue();
        // the refusal has to name both the cap and the uncapped way to do it, or the agent just retries
        assertThat(textOf(result)).contains("14 days per call", "REST API");
        verify(0, getRequestedFor(urlPathEqualTo("/db/i18n/myday20/")));
    }

    private void stubFddb(LocalDate date, String responseFile) {
        stubFor(get(urlPathEqualTo("/db/i18n/myday20/"))
                .withQueryParam("lang", equalTo("en"))
                .withQueryParam("q", equalTo(String.valueOf(
                        date.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC))))
                .withQueryParam("p", equalTo(String.valueOf(
                        date.atStartOfDay().toEpochSecond(ZoneOffset.UTC))))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile(responseFile)
                        .withHeader("Content-Type", "application/html")));
    }

    private String callTool(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertThat(result.isError()).as("tool %s returned an error: %s", name, textOf(result)).isNotEqualTo(true);
        return textOf(result);
    }

    private String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .reduce("", String::concat);
    }
}
