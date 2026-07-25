package dev.itobey.adapter.api.fddb.exporter.it;

import dev.itobey.adapter.api.fddb.exporter.config.TestConfig;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the MCP server the way a real client does: over HTTP, with the official MCP SDK client.
 * <p>
 * The unit tests cover what each tool returns; what they cannot cover is whether the tools are
 * actually discoverable over the transport, whether Spring AI derives a usable input schema from
 * the method signatures, and whether the results survive JSON serialization - which is where a
 * date silently turning into an object array would break every client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fddb-exporter.mcp.enabled=true",
        "fddb-exporter.persistence.influxdb.enabled=false"
})
@Testcontainers
@ActiveProfiles("test")
@Import(TestConfig.class)
class McpServerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(FddbData.class);
        fddbDataRepository.saveAll(List.of(
                day(LocalDate.of(2024, 1, 1), 2000, product("Haferflocken kernig", 300)),
                day(LocalDate.of(2024, 1, 2), 2500, product("Haferflocken kernig", 350)),
                day(LocalDate.of(2024, 1, 6), 3500, product("Pizza Salami", 1200))));

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
    void listTools_shouldExposeEveryReadOnlyToolWithAUsableInputSchema() {
        McpSchema.ListToolsResult tools = mcpClient.listTools();

        assertThat(tools.tools()).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("get_day", "get_days", "search_products", "get_stats",
                        "get_averages", "get_data_schema");
        assertThat(tools.tools()).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.annotations().readOnlyHint()).isTrue();
        });

        McpSchema.Tool getDay = tools.tools().stream()
                .filter(tool -> "get_day".equals(tool.name()))
                .findFirst()
                .orElseThrow();
        assertThat(getDay.inputSchema()).containsEntry("required", List.of("date"));
        assertThat(getDay.inputSchema().get("properties").toString()).contains("date");
    }

    @Test
    void getDay_shouldReturnTheDayAsJsonWithoutTheDatabaseId() {
        String documentId = fddbDataRepository.findFirstByDate(LocalDate.of(2024, 1, 1)).orElseThrow().getId();
        String result = callTool("get_day", Map.of("date", "2024-01-01"));

        assertThat(result).contains("\"date\":\"2024-01-01\"", "\"found\":true",
                "\"totalCalories\":2000", "Haferflocken kernig");
        assertThat(documentId).isNotBlank();
        assertThat(result).doesNotContain(documentId);
    }

    @Test
    void getDay_shouldReportADayWithoutAnEntryAsNotFound() {
        String result = callTool("get_day", Map.of("date", "2024-01-03"));

        assertThat(result).contains("\"found\":false", "No entry was logged for 2024-01-03");
        // the MCP results drop their null fields, so an absent entry does not cost a payload
        assertThat(result).doesNotContain("\"entry\"");
    }

    @Test
    void getDays_shouldOmitTheProductsUnlessTheyWereAskedFor() {
        String withoutProducts = callTool("get_days",
                Map.of("fromDate", "2024-01-01", "toDate", "2024-01-06"));

        assertThat(withoutProducts).contains("\"daysRequested\":6", "\"entryCount\":3");
        assertThat(withoutProducts).doesNotContain("Haferflocken kernig");

        String withProducts = callTool("get_days",
                Map.of("fromDate", "2024-01-01", "toDate", "2024-01-06", "includeProducts", true));

        assertThat(withProducts).contains("Haferflocken kernig", "Pizza Salami");
    }

    @Test
    void searchProducts_shouldReportThatItTruncatedTheResult() {
        String result = callTool("search_products", Map.of("name", "hafer", "limit", 1));

        assertThat(result).contains("\"truncated\":true", "\"resultCount\":1", "Haferflocken kernig");
    }

    @Test
    void getAverages_shouldAcceptRelativeDatesAndAverageOnlyLoggedDays() {
        String result = callTool("get_averages", Map.of("fromDate", "2024-01-01", "toDate", "2024-01-02"));

        assertThat(result).contains("\"avgTotalCalories\":2250");
    }

    @Test
    void getStats_shouldDescribeTheWholeDataset() {
        String result = callTool("get_stats", Map.of());

        assertThat(result).contains("\"amountEntries\":3", "\"firstEntryDate\":\"2024-01-01\"",
                "\"lastEntryDate\":\"2024-01-06\"");
    }

    @Test
    void getDataSchema_shouldReturnThePlainTextDataDictionary() {
        String result = callTool("get_data_schema", Map.of());

        assertThat(result).contains("totalCalories", "kcal", "NOT a number");
    }

    @Test
    void callTool_shouldReportAnUnparseableDateAsAToolErrorInsteadOfFailingTheCall() {
        McpSchema.CallToolResult result =
                mcpClient.callTool(new McpSchema.CallToolRequest("get_day", Map.of("date", "last tuesday")));

        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("last tuesday");
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

    private FddbData day(LocalDate date, double calories, Product... products) {
        FddbData data = new FddbData();
        data.setDate(date);
        data.setTotalCalories(calories);
        data.setTotalFat(100);
        data.setTotalCarbs(200);
        data.setTotalProtein(50);
        data.setTotalSugar(20);
        data.setTotalFibre(10);
        data.setProducts(List.of(products));
        return data;
    }

    private Product product(String name, double calories) {
        Product product = new Product();
        product.setName(name);
        product.setAmount("100 g");
        product.setCalories(calories);
        return product;
    }
}
