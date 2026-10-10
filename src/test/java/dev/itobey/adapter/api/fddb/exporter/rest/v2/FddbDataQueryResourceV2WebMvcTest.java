package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc test pinning the <strong>wire format</strong> of the v2 Query API — not its logic, which
 * {@link FddbDataQueryResourceV2Test} covers.
 * <p>
 * This exists as a safety net for the pending Jackson unification: the REST layer is serialized by Spring Boot 4's
 * Jackson 3 mapper, while {@code DataDownloadService} serializes the JSON download with a hand-built Jackson 2
 * mapper. Both currently render a {@code LocalDate} as an ISO-8601 string, but only by coincidence of two
 * independent configurations. These assertions are deliberately literal (raw response body, not just
 * {@code jsonPath}) so that a change of mapper, of {@code WRITE_DATES_AS_TIMESTAMPS}, of property inclusion or of
 * pretty-printing fails here rather than in a consumer.
 */
@WebMvcTest(FddbDataQueryResourceV2.class)
@ActiveProfiles("test")
@Tag("v2")
class FddbDataQueryResourceV2WebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FddbDataService fddbDataService;

    private static FddbDataDTO entryWithProducts() {
        ProductDTO banana = new ProductDTO("Banana", "100 g", 89.0, 0.3, 23.0, 1.1,
                "/db/en/food/banana/index.html");
        return new FddbDataDTO("507f1f77bcf86cd799439011", LocalDate.of(2024, 1, 1), List.of(banana),
                2000.5, 70.3, 250.2, 50.1, 100.4, 30.6);
    }

    private static FddbDataDTO entryWithoutProducts() {
        return new FddbDataDTO("507f1f77bcf86cd799439012", LocalDate.of(2024, 1, 2), null,
                2000.0, 70.0, 250.0, 50.0, 100.0, 30.0);
    }

    @Test
    @SneakyThrows
    void findAllEntries_shouldSerializeDateAsIsoString() {
        when(fddbDataService.findAllEntries()).thenReturn(List.of(entryWithProducts()));

        mockMvc.perform(get("/api/v2/fddbdata"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // the contract every consumer depends on: an ISO-8601 string, not a [y,m,d] array and not a number
                .andExpect(content().string(containsString("\"date\":\"2024-01-01\"")))
                .andExpect(content().string(not(containsString("[2024,1,1]"))))
                .andExpect(jsonPath("$[0].date").value("2024-01-01"));
    }

    @Test
    @SneakyThrows
    void findAllEntries_shouldSerializeTotalsAsPlainNumbersWithTotalPrefix() {
        when(fddbDataService.findAllEntries()).thenReturn(List.of(entryWithProducts()));

        mockMvc.perform(get("/api/v2/fddbdata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("507f1f77bcf86cd799439011"))
                .andExpect(jsonPath("$[0].totalCalories").value(2000.5))
                .andExpect(jsonPath("$[0].totalFat").value(70.3))
                .andExpect(jsonPath("$[0].totalCarbs").value(250.2))
                .andExpect(jsonPath("$[0].totalSugar").value(50.1))
                .andExpect(jsonPath("$[0].totalProtein").value(100.4))
                .andExpect(jsonPath("$[0].totalFibre").value(30.6))
                // doubles stay JSON numbers, never strings — the CSV download is the only place they get formatted
                .andExpect(content().string(containsString("\"totalCalories\":2000.5")))
                .andExpect(content().string(containsString("\"totalSugar\":50.1")));
    }

    @Test
    @SneakyThrows
    void findAllEntries_shouldRenderWholeDoublesWithTrailingZero() {
        when(fddbDataService.findAllEntries()).thenReturn(List.of(entryWithoutProducts()));

        mockMvc.perform(get("/api/v2/fddbdata"))
                .andExpect(status().isOk())
                // 2000.0, not 2000 — the fields are primitive doubles
                .andExpect(content().string(containsString("\"totalCalories\":2000.0")))
                .andExpect(content().string(containsString("\"totalFibre\":30.0")));
    }

    @Test
    @SneakyThrows
    void findAllEntries_shouldIncludeNullProductsAndNotPrettyPrint() {
        when(fddbDataService.findAllEntries()).thenReturn(List.of(entryWithoutProducts()));

        mockMvc.perform(get("/api/v2/fddbdata"))
                .andExpect(status().isOk())
                // null properties are emitted (no NON_NULL inclusion on the REST DTOs)
                .andExpect(content().string(containsString("\"products\":null")))
                // and the REST response is compact — unlike the JSON download, which is indented
                .andExpect(content().string(not(containsString("\n"))))
                .andExpect(content().string(not(containsString("\"date\" : "))));
    }

    @Test
    @SneakyThrows
    void findAllEntries_shouldSerializeNestedProductFields() {
        when(fddbDataService.findAllEntries()).thenReturn(List.of(entryWithProducts()));

        mockMvc.perform(get("/api/v2/fddbdata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].products[0].name").value("Banana"))
                .andExpect(jsonPath("$[0].products[0].amount").value("100 g"))
                .andExpect(jsonPath("$[0].products[0].calories").value(89.0))
                .andExpect(jsonPath("$[0].products[0].fat").value(0.3))
                .andExpect(jsonPath("$[0].products[0].carbs").value(23.0))
                .andExpect(jsonPath("$[0].products[0].protein").value(1.1))
                .andExpect(jsonPath("$[0].products[0].link").value("/db/en/food/banana/index.html"));
    }

    @Test
    @SneakyThrows
    void findByDateRange_shouldSerializeDateAsIsoStringToo() {
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 2);
        when(fddbDataService.findByDateRange(fromDate, toDate, false))
                .thenReturn(List.of(entryWithProducts(), entryWithoutProducts()));

        mockMvc.perform(get("/api/v2/fddbdata/range")
                        .param("fromDate", "2024-01-01")
                        .param("toDate", "2024-01-02"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"date\":\"2024-01-01\"")))
                .andExpect(content().string(containsString("\"date\":\"2024-01-02\"")))
                .andExpect(jsonPath("$[0].date").value("2024-01-01"))
                .andExpect(jsonPath("$[1].date").value("2024-01-02"));
    }
}
