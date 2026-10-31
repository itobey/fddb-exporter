package dev.itobey.adapter.api.fddb.exporter.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.config.TestConfig;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.InfluxDBService;
import lombok.SneakyThrows;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "fddb-exporter.timezone=Europe/Berlin")
@Testcontainers
@EnableFeignClients
@ActiveProfiles("test")
@Import(TestConfig.class)
class UpdateEntryIT {

    public static final String ADMIN_TOKEN = "token";
    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @Container
    @ServiceConnection
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
    private FddbExporterProperties properties;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("fddb-exporter.fddb.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("fddb-exporter.influxdb.url", () -> "http://localhost:" + influxDBContainer.getMappedPort(8086));
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        wireMockServer.resetAll();
        // prepare the database with an existing entry
        // deletes the documents rather than the collection: dropping it would take the declared indexes
        // with it, and the unique index on date is part of what these tests exercise
        mongoTemplate.remove(new Query(), FddbData.class);
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

    @Test
    @SneakyThrows
    void resyncAfterDayDeletedInFddbPreservesBothDatabases() {
        DateRangeDTO range = new DateRangeDTO("2024-09-06", "2024-09-06");
        stubFddbResponse();
        ExportResultDTO initialExport = fddbDataService.exportForTimerange(range);
        assertMongoDbEntries(initialExport);
        assertInfluxDbEntries();

        FddbData savedEntry = fddbDataRepository.findAll().getFirst();
        var savedTotals = new HashMap<>(readInfluxDbRecord().getValues());
        String emptyDiary = new ClassPathResource("no-data-available.html")
                .getContentAsString(StandardCharsets.UTF_8);
        stubFor(get(urlPathEqualTo("/db/i18n/myday20/"))
                .willReturn(ok(emptyDiary).withHeader("Content-Type", "text/html")));

        // Repeated syncs of a deleted source day must preserve the archived day in both stores.
        for (int attempt = 0; attempt < 2; attempt++) {
            ExportResultDTO result = fddbDataService.exportForTimerange(range);

            assertThat(result.getSuccessfulDays()).isEmpty();
            assertThat(result.getUnsuccessfulDays()).containsExactly("2024-09-06");
            List<FddbData> entries = fddbDataRepository.findAll();
            assertThat(entries).hasSize(1);
            assertThat(entries.getFirst()).usingRecursiveComparison().isEqualTo(savedEntry);
            assertThat(readInfluxDbRecord().getValues()).isEqualTo(savedTotals);
        }
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
        FluxRecord record1 = readInfluxDbRecord();
        ZonedDateTime expectedTime2 = ZonedDateTime.of(2024, 9, 6, 0, 0, 0, 0, properties.resolveZone());
        AssertionsForClassTypes.assertThat(record1.getTime()).isEqualTo(expectedTime2.toInstant());
        AssertionsForClassTypes.assertThat(record1.getValueByKey("calories")).isEqualTo(2656.0);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("fat")).isEqualTo(131.7);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("carbs")).isEqualTo(195.5);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("sugar")).isEqualTo(50.4);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("protein")).isEqualTo(113.9);
        AssertionsForClassTypes.assertThat(record1.getValueByKey("fibre")).isEqualTo(14.6);
    }

    private FluxRecord readInfluxDbRecord() {
        String query = """
                from(bucket:"test-bucket")
                  |> range(start: 0)
                  |> filter(fn: (r) => r._measurement == "dailyTotals")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> drop(columns: ["_start", "_stop"])
                  |> yield(name: "result")""";

        List<FluxTable> tables = influxDBClient.getQueryApi().query(query);

        assertThat(tables).hasSize(1);
        assertThat(tables.getFirst().getRecords()).hasSize(1);

        return tables.getFirst().getRecords().getFirst();
    }

}
