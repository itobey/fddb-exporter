package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportProgressDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies that {@link FddbDataClient} builds the correct request URIs for the product endpoints
 * and parses their responses. The client is bound to a {@link MockRestServiceServer} so no real
 * HTTP call is made.
 */
class FddbDataClientTest {

    // deliberately not port 8080 and not the empty context path: the base URL has to come from the
    // configuration, not from a literal in the client
    private static final String BASE = "http://localhost:9090/fddb/api/v2/fddbdata";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FddbDataClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new FddbDataClient(restTemplate, new ApiBaseUrl(9090, "/fddb", false));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void recentProgressUsesRecentEndpointAndIncludeToday(boolean includeToday) throws ApiException {
        server.expect(requestTo(BASE + "/export/progress?days=7&includeToday=" + includeToday))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
                        "{\"date\":\"2026-10-01\",\"successful\":true,\"complete\":false,\"error\":null}\n"
                        + "{\"date\":null,\"successful\":false,\"complete\":true,\"error\":null}\n",
                        MediaType.parseMediaType("application/x-ndjson")));
        var events = new ArrayList<ExportProgressDTO>();
        client.exportRecentWithProgress(7, includeToday, events::add);
        assertThat(events).hasSize(2);
        assertThat(events.getFirst().date()).isEqualTo("2026-10-01");
        assertThat(events.getLast().complete()).isTrue();
        server.verify();
    }

    @Test
    void progressStreamDeliversDayEventsAndRequiresCompletion() throws ApiException {
        String day = "{\"date\":\"2026-10-01\",\"successful\":true,\"complete\":false,\"error\":null}\n";
        String end = "{\"date\":null,\"successful\":false,\"complete\":true,\"error\":null}\n";
        server.expect(requestTo(BASE + "/export/progress")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(day + end, MediaType.parseMediaType("application/x-ndjson")));
        server.expect(requestTo(BASE + "/export/progress"))
                .andRespond(withSuccess(day, MediaType.parseMediaType("application/x-ndjson")));
        var range = new DateRangeDTO("2026-10-01", "2026-10-02");
        var events = new ArrayList<ExportProgressDTO>();
        client.exportWithProgress(range, events::add);
        assertThat(events).hasSize(2);
        assertThat(events.getFirst().date()).isEqualTo("2026-10-01");
        assertThat(events.getLast().complete()).isTrue();
        assertThatThrownBy(() -> client.exportWithProgress(range, progress -> { }))
                .isInstanceOf(ApiException.class).hasMessageContaining("ended before completion");
        server.verify();
    }

    @Test
    void getByDate_shouldTreatMissingDiaryDayAsAnEmptyResult() throws ApiException {
        server.expect(requestTo(BASE + "/2026-10-01"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getByDate("2026-10-01")).isNull();
        server.verify();
    }

    @Test
    void getByDate_shouldKeepServiceFailuresDistinctFromMissingDays() {
        server.expect(requestTo(BASE + "/2026-10-01"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":503,\"detail\":\"MongoDB is unavailable\"}"));

        assertThatThrownBy(() -> client.getByDate("2026-10-01"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MongoDB is unavailable");
        server.verify();
    }

    @Test
    void getAllEntries_shouldShowProblemDetailMessage() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":503,\"detail\":\"MongoDB is unavailable\"}"));

        assertThatThrownBy(client::getAllEntries)
                .isInstanceOf(ApiException.class)
                .hasMessage("Failed to retrieve entries: MongoDB is unavailable");
        server.verify();
    }

    @Test
    void getDistinctProductNames_shouldIncludeSearchAndLimit() throws ApiException {
        server.expect(requestTo(BASE + "/products/distinct?limit=50&search=hafer"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[\"Haferflocken kernig\",\"Haferflocken zart\"]", MediaType.APPLICATION_JSON));

        List<String> result = client.getDistinctProductNames("hafer", 50);

        server.verify();
        assertThat(result).containsExactly("Haferflocken kernig", "Haferflocken zart");
    }

    @Test
    void getDistinctProductNames_shouldOmitBlankSearch() throws ApiException {
        server.expect(requestTo(BASE + "/products/distinct?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<String> result = client.getDistinctProductNames("  ", 100);

        server.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void getProductSummary_shouldIncludeNameAndDateRange() throws ApiException {
        String body = """
                {
                  "searchTerm": "Haferflocken",
                  "matchedProductNames": ["Haferflocken kernig"],
                  "timesEaten": 42,
                  "firstDate": "2024-01-03",
                  "lastDate": "2024-12-19",
                  "totalCalories": 12600.5,
                  "totalFat": 310.2,
                  "totalCarbs": 1850.7,
                  "totalProtein": 540.3,
                  "averageCalories": 300.0,
                  "weekdayDistribution": {"MONDAY": 3, "FRIDAY": 2}
                }
                """;
        server.expect(requestTo(BASE + "/products/summary?name=Haferflocken&fromDate=2024-01-01&toDate=2024-12-31"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ProductSummaryDTO result = client.getProductSummary("Haferflocken", "2024-01-01", "2024-12-31");

        server.verify();
        assertThat(result.getTimesEaten()).isEqualTo(42);
        assertThat(result.getWeekdayDistribution())
                .containsEntry(DayOfWeek.MONDAY, 3L)
                .containsEntry(DayOfWeek.FRIDAY, 2L);
    }

    @Test
    void getProductSummary_shouldOmitNullDates() throws ApiException {
        server.expect(requestTo(BASE + "/products/summary?name=Banana"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"searchTerm\":\"Banana\",\"timesEaten\":1,\"totalCalories\":89.0,"
                        + "\"totalFat\":0.3,\"totalCarbs\":23.0,\"totalProtein\":1.1,\"averageCalories\":89.0}",
                        MediaType.APPLICATION_JSON));

        ProductSummaryDTO result = client.getProductSummary("Banana", null, null);

        server.verify();
        assertThat(result.getSearchTerm()).isEqualTo("Banana");
    }

    @Test
    void searchProducts_shouldIncludeDaysDateRangeAndLimit() throws ApiException {
        server.expect(requestTo(BASE + "/products?name=Banana&days=MONDAY&days=FRIDAY&fromDate=2024-01-01&toDate=2024-12-31&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<ProductWithDateDTO> result = client.searchProducts(
                "Banana", List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "2024-01-01", "2024-12-31", 100);

        server.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void searchProducts_shouldParseTheOccurrenceDate() throws ApiException {
        String body = "[{\"date\":\"2024-12-19\",\"product\":{\"name\":\"Sushi Box\",\"amount\":\"250 g\","
                + "\"calories\":350.0,\"fat\":5.0,\"carbs\":60.0,\"protein\":12.0,\"link\":\"/x\"}}]";
        server.expect(requestTo(BASE + "/products?name=sushi&limit=500"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ProductWithDateDTO> result = client.searchProducts("sushi", null, null, null, 500);

        server.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(java.time.LocalDate.of(2024, 12, 19));
        assertThat(result.get(0).getProduct().getName()).isEqualTo("Sushi Box");
    }

    @Test
    void searchProducts_shouldOmitOptionalParamsWhenNull() throws ApiException {
        server.expect(requestTo(BASE + "/products?name=Banana"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<ProductWithDateDTO> result = client.searchProducts("Banana", null, null, null, null);

        server.verify();
        assertThat(result).isEmpty();
    }
}
