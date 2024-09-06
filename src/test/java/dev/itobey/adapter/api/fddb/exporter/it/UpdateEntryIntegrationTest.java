package dev.itobey.adapter.api.fddb.exporter.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportRequestDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
@EnableFeignClients
@WireMockTest
@ActiveProfiles("test")
class UpdateEntryIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.9");

    @Autowired
    private FddbDataService fddbDataService;

    @Autowired
    private FddbDataRepository fddbDataRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static int wireMockPort;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("fddb-exporter.fddb.url", () -> "http://localhost:" + wireMockPort);
    }

    @BeforeAll
    static void beforeAll(WireMockRuntimeInfo wmRuntimeInfo) {
        wireMockPort = wmRuntimeInfo.getHttpPort();
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        // prepare the database with an existing entry
        mongoTemplate.dropCollection(FddbData.class);
        ClassPathResource resource = new ClassPathResource("__files/update/existing-incomplete-entry.json");
        FddbData testData = objectMapper.readValue(resource.getInputStream(), FddbData.class);
        fddbDataRepository.save(testData);
    }

    @Test
    @SneakyThrows
    void testSaveOrUpdateUpdatesExistingEntry() {
        stubFddbResponse();

        ExportRequestDTO exportRequestDTO = ExportRequestDTO.builder()
                .fromDate("2024-09-06")
                .toDate("2024-09-06")
                .build();
        ExportResultDTO exportResultDTO = fddbDataService.exportForTimerange(exportRequestDTO);

        List<FddbData> allEntries = fddbDataRepository.findAll();

        assertThat(allEntries.size()).isEqualTo(1);
        assertThat(exportResultDTO.getSuccessfulDays()).containsExactly("2024-09-06");
        FddbData fddbData = allEntries.getFirst();
        assertNotNull(fddbData);
        ClassPathResource expectedResource = new ClassPathResource("__files/update/expected-after-update.json");
        FddbData expected = objectMapper.readValue(expectedResource.getInputStream(), FddbData.class);
        assertThat(fddbData).isEqualTo(expected);
    }

    private void stubFddbResponse() {
        LocalDate date = LocalDate.of(2024, 9, 6);
        stubFor(get(urlPathEqualTo("/db/i18n/myday20/"))
                .withQueryParam("lang", equalTo("en"))
                .withQueryParam("q", equalTo(String.valueOf(date.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC))))
                .withQueryParam("p", equalTo(String.valueOf(date.atStartOfDay().toEpochSecond(ZoneOffset.UTC))))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("update/complete-data-2024-09-06.html")
                        .withHeader("Content-Type", "application/html")));
    }

}