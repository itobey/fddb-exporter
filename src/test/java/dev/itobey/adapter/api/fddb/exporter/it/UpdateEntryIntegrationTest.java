package dev.itobey.adapter.api.fddb.exporter.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import dev.itobey.adapter.api.fddb.exporter.config.TestConfig;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.InfluxDBService;
import lombok.SneakyThrows;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
@EnableFeignClients
@WireMockTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class UpdateEntryIntegrationTest {

    public static final String ADMIN_TOKEN = "token";
    private static int wireMockPort;

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.9");
    @Container
    static InfluxDBContainer<?> influxDBContainer =
            new InfluxDBContainer<>(DockerImageName.parse("influxdb:2.0.7"))
                    .withAdminToken(ADMIN_TOKEN);

    @Autowired
    private FddbDataService fddbDataService;
    @Autowired
    private FddbDataRepository fddbDataRepository;
    @Autowired
    private InfluxDBService influxDBService;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private InfluxDBClient influxDBClient;
    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("fddb-exporter.fddb.url", () -> "http://localhost:" + wireMockPort);
        registry.add("fddb-exporter.influxdb.url", () -> "http://localhost:" + influxDBContainer.getMappedPort(8086));
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
        influxDBService.saveToInfluxDB(testData);
    }

    @Test
    @SneakyThrows
    void testSaveOrUpdateUpdatesExistingEntry() {
        stubFddbResponse();

        DateRangeDTO dateRangeDTO = DateRangeDTO.builder()
                .fromDate("2024-09-06")
                .toDate("2024-09-06")
                .build();
        ExportResultDTO exportResultDTO = fddbDataService.exportForTimerange(dateRangeDTO);

        assertMongoDbEntries(exportResultDTO);
        assertInfluxDbEntries();
    }

    private void assertMongoDbEntries(ExportResultDTO exportResultDTO) throws IOException {
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

    private void assertInfluxDbEntries() {
        String query = """
                from(bucket:"test-bucket")
                  |> range(start: 0)
                  |> filter(fn: (r) => r._measurement == "dailyTotals")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> yield(name: "result")""";

        List<FluxTable> tables = influxDBClient.getQueryApi().query(query);

        assertThat(tables).hasSize(1);
        assertThat(tables.getFirst().getRecords()).hasSize(1);

        FluxRecord record1 = tables.getFirst().getRecords().getFirst();
        ZonedDateTime expectedTime2 = ZonedDateTime.of(2024, 9, 6, 0, 0, 0, 0, ZoneId.systemDefault());
        AssertionsForClassTypes.assertThat(record1.getTime()).isEqualTo(expectedTime2.toInstant());
        AssertionsForClassTypes.assertThat(record1.getValueByKey("calories")).isEqualTo(2656.0);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("fat")).isEqualTo(131.7);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("carbs")).isEqualTo(195.5);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("sugar")).isEqualTo(50.4);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("protein")).isEqualTo(113.9);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("fibre")).isEqualTo(14.6);

    }

}