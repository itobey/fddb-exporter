package dev.itobey.adapter.api.fddb.exporter.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportRequestDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.PersistenceService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * This test acts as an e2e test to verify the application is working correctly.
 * Wiremock will serve HTML of FDDB sites and the application will parse these and save the results in a MongoDB container.
 */
@SpringBootTest
@Testcontainers
@EnableFeignClients
@WireMockTest
@ActiveProfiles("test")
class ApplicationIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.9");

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private FddbDataService fddbDataService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static int wireMockPort;

    @BeforeAll
    static void beforeAll(WireMockRuntimeInfo wmRuntimeInfo) {
        wireMockPort = wmRuntimeInfo.getHttpPort();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("fddb-exporter.fddb.url", () -> "http://localhost:" + wireMockPort);
    }

    @Test
    @SneakyThrows
    void shouldSkipParseErrorsAndContinueProcessing() {
        // given
        stubFddbResponses();

        // when
        ExportRequestDTO exportRequestDTO = ExportRequestDTO.builder()
                .fromDate("2024-08-27")
                .toDate("2024-08-29")
                .build();
        ExportResultDTO exportResultDTO = fddbDataService.exportForTimerange(exportRequestDTO);

        // then
        assertExportResult(exportResultDTO);
        assertDatabaseEntries();
    }

    private void stubFddbResponses() {
        Map<LocalDate, String> dateToResponseFile = Map.of(
                LocalDate.of(2024, 8, 27), "data-available-2024-08-27.html",
                LocalDate.of(2024, 8, 28), "no-data-2024-08-28.html",
                LocalDate.of(2024, 8, 29), "data-available-2024-08-29.html"
        );

        dateToResponseFile.forEach((date, fileName) ->
                stubFor(get(urlPathEqualTo("/db/i18n/myday20/"))
                        .withQueryParam("lang", equalTo("en"))
                        .withQueryParam("q", equalTo(String.valueOf(date.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC))))
                        .withQueryParam("p", equalTo(String.valueOf(date.atStartOfDay().toEpochSecond(ZoneOffset.UTC))))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBodyFile(fileName)
                                .withHeader("Content-Type", "application/html"))
                ));
    }

    private void assertExportResult(ExportResultDTO exportResultDTO) {
        assertThat(exportResultDTO.getSuccessfulDays())
                .containsExactlyInAnyOrder("2024-08-27", "2024-08-29");
        assertThat(exportResultDTO.getUnsuccessfulDays())
                .containsExactly("2024-08-28");
    }

    @SneakyThrows
    private void assertDatabaseEntries() {
        List<FddbData> allEntries = persistenceService.findAllEntries();
        assertThat(allEntries).hasSize(2);

        FddbData fddbData27 = objectMapper.readValue(
                new ClassPathResource("domain/fddbdata-2024-08-27.json").getFile(),
                FddbData.class
        );
        FddbData fddbData29 = objectMapper.readValue(
                new ClassPathResource("domain/fddbdata-2024-08-29.json").getFile(),
                FddbData.class
        );

        assertThat(allEntries)
                .usingRecursiveComparison()
                .ignoringFields("id")
                .ignoringCollectionOrder()
                .isEqualTo(List.of(fddbData27, fddbData29));
    }
}