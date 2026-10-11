package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import dev.itobey.adapter.api.fddb.exporter.testutil.TestDataLoader;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataDownloadServiceTest {

    private static final String TEST_DATA_PATH = "testdata/download-service/";

    @Test
    void downloadData_shouldAllow365Entries() {
        when(persistenceService.countAllEntries()).thenReturn(365L);
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(testFddbDataDTO);

        assertThat(dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, "."))
                .isNotEmpty();
        verify(persistenceService).findAllEntries();
    }

    @Test
    void downloadData_shouldReject366EntriesBeforeLoadingAllData() {
        when(persistenceService.countAllEntries()).thenReturn(366L);

        assertThatThrownBy(() -> dataDownloadService.downloadData(null, null, DownloadFormat.CSV, false, "."))
                .isInstanceOf(DataDownloadService.DownloadTooLargeException.class)
                .hasMessageContaining("366 days")
                .hasMessageContaining("maximum of 365 days");
        verify(persistenceService, never()).findAllEntries();
        verifyNoInteractions(fddbDataMapper);
    }

    @Test
    void downloadData_shouldReject366EntriesInDateRangeBeforeMapping() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(persistenceService.findByDateBetween(from, to))
                .thenReturn(java.util.Collections.nCopies(366, testFddbData.getFirst()));

        assertThatThrownBy(() -> dataDownloadService.downloadData(from, to, DownloadFormat.JSON, true, "."))
                .isInstanceOf(DataDownloadService.DownloadTooLargeException.class)
                .hasMessageContaining("maximum of 365 days");
        verifyNoInteractions(fddbDataMapper);
    }

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private FddbDataMapper fddbDataMapper;

    private DataDownloadService dataDownloadService;

    private List<FddbData> testFddbData;
    private List<FddbDataDTO> testFddbDataDTO;

    @BeforeEach
    void setUp() {
        // Match Boot's Jackson 3 defaults: ISO dates by default, pretty-printing applied by the service.
        dataDownloadService = new DataDownloadService(persistenceService, fddbDataMapper, JsonMapper.builder().build());

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

    /**
     * Formula-like product fields are escaped even when CSV-quoted.
     */
    @Test
    @SneakyThrows
    void downloadData_whenAProductNameLooksLikeAFormula_shouldNeutralizeIt() {
        // given
        FddbDataDTO entry = new FddbDataDTO();
        entry.setDate(LocalDate.of(2024, 1, 1));
        entry.setTotalCalories(2000);
        ProductDTO product = new ProductDTO();
        product.setName("=1+1");
        product.setAmount("@100 g");
        product.setLink("+http://example.org");
        entry.setProducts(List.of(product));

        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(new ArrayList<>(List.of(entry)));

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, true, ".");

        // then: formula-like fields have a leading apostrophe
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=1+1\"");
        assertThat(csv).contains("\"'@100 g\"");
        assertThat(csv).contains("\"'+http://example.org\"");
        assertThat(csv).doesNotContain("\"=1+1\"");
    }

    /**
     * Ordinary fields retain their original CSV representation.
     */
    @Test
    @SneakyThrows
    void downloadData_whenNothingLooksLikeAFormula_shouldLeaveEveryFieldAlone() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.CSV, true, ".");

        // then
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("'");
        assertThat(csv).contains("\"2024-01-01\"");
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

    /**
     * Pins the JSON download's date format. Download and REST API now share one mapper - Spring Boot 4's
     * autoconfigured Jackson 3 {@code JsonMapper} - so this assertion and its REST counterpart
     * ({@code rest/v2/FddbDataQueryResourceV2WebMvcTest}) pin the two ends of the same configuration and must
     * keep agreeing.
     */
    @Test
    @SneakyThrows
    void downloadData_json_shouldSerializeDateAsIsoStringNotTimestamp() {
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
        // WRITE_DATES_AS_TIMESTAMPS must stay disabled: no [2024,1,1] array, no epoch number
        assertThat(json).doesNotContain("2024,1,1");
        assertThat(json).doesNotContain("\"date\" : [");
    }

    /**
     * Pins that the JSON download is pretty-printed: two-space indentation and {@code " : "} around the
     * key/value separator. This is the one place where the download deliberately differs from the compact REST
     * responses, and since both share a mapper it has to come from a per-write
     * {@code writerWithDefaultPrettyPrinter()} rather than from mapper state.
     */
    @Test
    @SneakyThrows
    void downloadData_json_shouldBeIndentedWithTwoSpaces() {
        // given
        when(persistenceService.findAllEntries()).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-single-day.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(null, null, DownloadFormat.JSON, false, ".");

        // then - normalise the line separator, Jackson's DefaultPrettyPrinter uses the platform one
        String json = new String(result, StandardCharsets.UTF_8).replace("\r\n", "\n");
        // Jackson's DefaultPrettyPrinter keeps array elements inline and indents object properties by two spaces
        assertThat(json).startsWith("[ {");
        assertThat(json).contains("\n  \"date\" : \"2024-01-01\"");
        assertThat(json).contains("\n  \"totalCalories\" : 2000.0");
        // explicitly not the compact form the REST layer produces
        assertThat(json).doesNotContain("\"date\":\"2024-01-01\"");
        assertThat(json).doesNotContain("\"totalCalories\":2000.0");
    }

    /**
     * Pins the field naming and numeric rendering of the download: the same {@code total*} property names and plain
     * JSON numbers (never CSV-style formatted strings) that the REST API exposes.
     */
    @Test
    @SneakyThrows
    void downloadData_json_shouldUseTotalPrefixedNumericFields() {
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
        assertThat(json).contains("\"totalCalories\" : 2000.0");
        assertThat(json).contains("\"totalFat\" : 70.0");
        assertThat(json).contains("\"totalCarbs\" : 250.0");
        assertThat(json).contains("\"totalSugar\" : 50.0");
        assertThat(json).contains("\"totalProtein\" : 100.0");
        assertThat(json).contains("\"totalFibre\" : 30.0");
        // doubles stay numbers - the decimal separator option only applies to CSV
        assertThat(json).doesNotContain("\"2000.0\"");
        assertThat(json).doesNotContain("2000,0");
        // null properties are emitted, matching the REST API's inclusion behaviour
        assertThat(json).contains("\"id\" : null");
        assertThat(json).contains("\"products\" : null");
    }

    @Test
    @SneakyThrows
    void downloadData_whenDateRangeProvided_shouldQueryOnlyThatRange() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 2);
        LocalDate toDate = LocalDate.of(2024, 1, 2);
        when(persistenceService.findByDateBetween(fromDate, toDate)).thenReturn(testFddbData);
        when(fddbDataMapper.toFddbDataDTO(anyList())).thenReturn(testFddbDataDTO);

        List<FddbDataDTO> totalsOnly = TestDataLoader.loadListFromJson(
                TEST_DATA_PATH + "fddb-data-dto-date-range-filter.json", FddbDataDTO.class);
        when(fddbDataMapper.toFddbDataDTOWithoutProducts(anyList())).thenReturn(totalsOnly);

        // when
        byte[] result = dataDownloadService.downloadData(fromDate, toDate, DownloadFormat.CSV, false, ".");

        verify(persistenceService).findByDateBetween(fromDate, toDate);
        verify(persistenceService, never()).findAllEntries();

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
