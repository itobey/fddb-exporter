package dev.itobey.adapter.api.fddb.exporter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import dev.itobey.adapter.api.fddb.exporter.testutil.TestDataLoader;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDownloadServiceTest {

    private static final String TEST_DATA_PATH = "testdata/download-service/";

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private FddbDataMapper fddbDataMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DataDownloadService dataDownloadService;

    private List<FddbData> testFddbData;
    private List<FddbDataDTO> testFddbDataDTO;

    @BeforeEach
    void setUp() {
        // Setup ObjectMapper mock to return a working copy
        ObjectMapper mockMapperCopy = new ObjectMapper();
        mockMapperCopy.registerModule(new JavaTimeModule());
        mockMapperCopy.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMapperCopy.enable(SerializationFeature.INDENT_OUTPUT);
        when(objectMapper.copy()).thenReturn(mockMapperCopy);

        // Manually invoke the @PostConstruct method since @InjectMocks doesn't trigger it
        dataDownloadService.initJsonExportMapper();

        // Load test data from JSON fixtures
        testFddbData = TestDataLoader.loadListFromJson(TEST_DATA_PATH + "fddb-data-input.json", FddbData.class);
        testFddbDataDTO = TestDataLoader.loadListFromJson(TEST_DATA_PATH + "fddb-data-dto-input.json", FddbDataDTO.class);
    }

    @Test
    @SneakyThrows
    void downloadData_whenIncludeProductsIsFalse_shouldReturnCsvWithTotalsOnly() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-totals-only.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Date\";\"Calories\";\"Fat\";\"Carbs\";\"Sugar\";\"Protein\";\"Fibre\"");
        assertThat(csv).contains("\"2024-01-01\";\"2000.0\";\"70.0\";\"250.0\";\"50.0\";\"100.0\";\"30.0\"");
        assertThat(csv).contains("\"2024-01-02\";\"2100.0\";\"75.0\";\"260.0\";\"55.0\";\"110.0\";\"35.0\"");
        assertThat(csv).contains("\"2024-01-03\";\"1900.0\";\"65.0\";\"240.0\";\"45.0\";\"95.0\";\"28.0\"");
        verify(fddbDataMapper).toFddbDataDTOWithoutProducts(anyList());
    }

    @Test
    @SneakyThrows
    void downloadData_whenIncludeProductsIsTrue_shouldReturnCsvWithProducts() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, true, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Date\";\"Product Name\";\"Amount\";\"Calories\";\"Fat\";\"Carbs\";\"Protein\";\"Link\"");
        assertThat(csv).contains("Banana");
        assertThat(csv).contains("100 g");
        assertThat(csv).contains("Apple");
        assertThat(csv).contains("150 g");
    }

    @Test
    @SneakyThrows
    void downloadData_whenDecimalSeparatorIsComma_shouldUseCommaSeparator() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-decimal-test.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, ",");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("2000,5");
        assertThat(csv).contains("70,3");
        assertThat(csv).contains("250,2");
    }

    @Test
    @SneakyThrows
    void downloadData_whenDecimalSeparatorIsDot_shouldUseDotSeparator() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-decimal-test.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("2000.5");
        assertThat(csv).contains("70.3");
        assertThat(csv).contains("250.2");
    }

    @Test
    @SneakyThrows
    void downloadData_whenIncludeProductsIsFalse_shouldReturnJsonWithTotalsOnly() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-single-day.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);


        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.JSON, false, ".");

        // then
        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"date\" : \"2024-01-01\"");
        assertThat(json).contains("\"totalCalories\" : 2000.0");
        // The products field is present but null when not included
        assertThat(json).contains("\"products\" : null");
    }

    @Test
    @SneakyThrows
    void downloadData_whenIncludeProductsIsTrue_shouldReturnJsonWithProducts() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);


        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.JSON, true, ".");

        // then
        String json = new String(result, StandardCharsets.UTF_8);
        assertThat(json).contains("\"date\" : \"2024-01-01\"");
        assertThat(json).contains("\"products\"");
        assertThat(json).contains("\"name\" : \"Banana\"");
    }

    @Test
    @SneakyThrows
    void downloadData_whenDateRangeProvided_shouldFilterByDateRange() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 2);
        LocalDate toDate = LocalDate.of(2024, 1, 2);
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-date-range-filter.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(fromDate, toDate, DownloadFormat.CSV, false, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("2024-01-01");
        assertThat(csv).contains("2024-01-02");
        assertThat(csv).doesNotContain("2024-01-03");
    }

    @Test
    @SneakyThrows
    void downloadData_whenProductsListEmpty_shouldHandleEmptyProductsList() {
        // given
        List<FddbData> emptyProductsData = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-empty-products.json", FddbData.class);
        List<FddbDataDTO> emptyProductsDTOData = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-empty-products.json", FddbDataDTO.class);

        when(persistenceService.findAllEntries()).thenReturn(emptyProductsData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(emptyProductsDTOData);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, true, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("2024-01-01");
        assertThat(csv).contains("2000.0");
    }

    @Test
    @SneakyThrows
    void downloadData_whenUnsorted_shouldSortByDate() {
        // given
        List<FddbData> unsortedData = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-unsorted.json", FddbData.class);
        List<FddbDataDTO> unsortedDTOData = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-unsorted.json", FddbDataDTO.class);

        when(persistenceService.findAllEntries()).thenReturn(unsortedData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(unsortedDTOData);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-totals-only.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines[1]).contains("2024-01-01");
        assertThat(lines[2]).contains("2024-01-02");
        assertThat(lines[3]).contains("2024-01-03");
    }
}
