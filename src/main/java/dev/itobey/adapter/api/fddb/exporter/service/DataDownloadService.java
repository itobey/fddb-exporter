package dev.itobey.adapter.api.fddb.exporter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVWriter;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service for downloading FDDB data in various formats (CSV, JSON).
 * Supports downloading all data or data within a specific date range,
 * and can include full product details or just daily totals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataDownloadService {

    private static final char CSV_SEPARATOR = ';';
    private static final String COMMA_DECIMAL_SEPARATOR = ",";
    private static final String EMPTY_FIELD = "";

    private static final String[] TOTALS_CSV_HEADER = {
            "Date", "Calories", "Fat", "Carbs", "Sugar", "Protein", "Fibre"
    };

    private static final String[] FULL_DATA_CSV_HEADER = {
            "Date", "Product Name", "Amount", "Calories", "Fat", "Carbs", "Protein", "Link",
            "Day Total Calories", "Day Total Fat", "Day Total Carbs", "Day Total Sugar",
            "Day Total Protein", "Day Total Fibre"
    };

    private final PersistenceService persistenceService;
    private final FddbDataMapper fddbDataMapper;
    private final ObjectMapper objectMapper;

    private ObjectMapper jsonExportMapper;

    @PostConstruct
    void initJsonExportMapper() {
        jsonExportMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Downloads data in the specified format.
     *
     * @param fromDate         optional start date (null for all data)
     * @param toDate           optional end date (null for all data)
     * @param format           the download format (CSV or JSON)
     * @param includeProducts  whether to include product details
     * @param decimalSeparator the decimal separator for CSV format (. or ,)
     * @return the data as a byte array in the specified format
     */
    public byte[] downloadData(LocalDate fromDate, LocalDate toDate, DownloadFormat format,
                               boolean includeProducts, String decimalSeparator) {
        log.info("Downloading data: fromDate={}, toDate={}, format={}, includeProducts={}, decimalSeparator={}",
                fromDate, toDate, format, includeProducts, decimalSeparator);

        List<FddbDataDTO> data = fetchData(fromDate, toDate);

        return includeProducts
                ? exportFullData(data, format, decimalSeparator)
                : exportTotalsOnly(data, format, decimalSeparator);
    }

    private byte[] exportFullData(List<FddbDataDTO> data, DownloadFormat format, String decimalSeparator) {
        return format == DownloadFormat.CSV
                ? convertFullDataToCsv(data, decimalSeparator)
                : convertToJson(data);
    }

    private byte[] exportTotalsOnly(List<FddbDataDTO> data, DownloadFormat format, String decimalSeparator) {
        List<FddbDataDTO> totals = fddbDataMapper.toFddbDataDTOWithoutProducts(data);
        return format == DownloadFormat.CSV
                ? convertTotalsToCsv(totals, decimalSeparator)
                : convertToJson(totals);
    }

    private List<FddbDataDTO> fetchData(LocalDate fromDate, LocalDate toDate) {
        List<FddbDataDTO> allData = fddbDataMapper.toFddbDataDTO(persistenceService.findAllEntries());
        allData.sort(Comparator.comparing(FddbDataDTO::getDate));

        if (fromDate == null && toDate == null) {
            log.debug("Fetched {} entries for download", allData.size());
            return allData;
        }

        List<FddbDataDTO> filteredData = filterByDateRange(allData, fromDate, toDate);
        log.debug("Fetched {} entries for download", filteredData.size());
        return filteredData;
    }

    private List<FddbDataDTO> filterByDateRange(List<FddbDataDTO> data, LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveFromDate = Optional.ofNullable(fromDate).orElse(LocalDate.MIN);
        LocalDate effectiveToDate = Optional.ofNullable(toDate).orElse(LocalDate.MAX);

        return data.stream()
                .filter(d -> isWithinDateRange(d.getDate(), effectiveFromDate, effectiveToDate))
                .toList();
    }

    private boolean isWithinDateRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private byte[] convertTotalsToCsv(List<FddbDataDTO> totals, String decimalSeparator) {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = createCsvWriter(stringWriter)) {
            csvWriter.writeNext(TOTALS_CSV_HEADER);

            for (FddbDataDTO total : totals) {
                csvWriter.writeNext(createTotalsRow(total, decimalSeparator));
            }
        } catch (Exception exception) {
            log.error("Error generating CSV for totals", exception);
            throw new RuntimeException("Failed to generate CSV", exception);
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String[] createTotalsRow(FddbDataDTO total, String decimalSeparator) {
        return new String[]{
                total.getDate().toString(),
                formatNumber(total.getTotalCalories(), decimalSeparator),
                formatNumber(total.getTotalFat(), decimalSeparator),
                formatNumber(total.getTotalCarbs(), decimalSeparator),
                formatNumber(total.getTotalSugar(), decimalSeparator),
                formatNumber(total.getTotalProtein(), decimalSeparator),
                formatNumber(total.getTotalFibre(), decimalSeparator)
        };
    }

    private byte[] convertFullDataToCsv(List<FddbDataDTO> data, String decimalSeparator) {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = createCsvWriter(stringWriter)) {
            csvWriter.writeNext(FULL_DATA_CSV_HEADER);

            for (FddbDataDTO entry : data) {
                writeEntryRows(csvWriter, entry, decimalSeparator);
            }
        } catch (Exception exception) {
            log.error("Error generating CSV for full data", exception);
            throw new RuntimeException("Failed to generate CSV", exception);
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeEntryRows(CSVWriter csvWriter, FddbDataDTO entry, String decimalSeparator) {
        if (hasProducts(entry)) {
            for (ProductDTO product : entry.getProducts()) {
                csvWriter.writeNext(createProductRow(entry, product, decimalSeparator));
            }
        } else {
            csvWriter.writeNext(createEmptyProductRow(entry, decimalSeparator));
        }
    }

    private boolean hasProducts(FddbDataDTO entry) {
        return entry.getProducts() != null && !entry.getProducts().isEmpty();
    }

    private String[] createProductRow(FddbDataDTO entry, ProductDTO product, String decimalSeparator) {
        String[] dayTotals = createDayTotalsArray(entry, decimalSeparator);
        return new String[]{
                entry.getDate().toString(),
                product.getName(),
                product.getAmount(),
                formatNumber(product.getCalories(), decimalSeparator),
                formatNumber(product.getFat(), decimalSeparator),
                formatNumber(product.getCarbs(), decimalSeparator),
                formatNumber(product.getProtein(), decimalSeparator),
                product.getLink(),
                dayTotals[0], dayTotals[1], dayTotals[2], dayTotals[3], dayTotals[4], dayTotals[5]
        };
    }

    private String[] createEmptyProductRow(FddbDataDTO entry, String decimalSeparator) {
        String[] dayTotals = createDayTotalsArray(entry, decimalSeparator);
        return new String[]{
                entry.getDate().toString(),
                EMPTY_FIELD, EMPTY_FIELD, EMPTY_FIELD, EMPTY_FIELD, EMPTY_FIELD, EMPTY_FIELD, EMPTY_FIELD,
                dayTotals[0], dayTotals[1], dayTotals[2], dayTotals[3], dayTotals[4], dayTotals[5]
        };
    }

    private String[] createDayTotalsArray(FddbDataDTO entry, String decimalSeparator) {
        return new String[]{
                formatNumber(entry.getTotalCalories(), decimalSeparator),
                formatNumber(entry.getTotalFat(), decimalSeparator),
                formatNumber(entry.getTotalCarbs(), decimalSeparator),
                formatNumber(entry.getTotalSugar(), decimalSeparator),
                formatNumber(entry.getTotalProtein(), decimalSeparator),
                formatNumber(entry.getTotalFibre(), decimalSeparator)
        };
    }

    private CSVWriter createCsvWriter(StringWriter stringWriter) {
        return new CSVWriter(stringWriter, CSV_SEPARATOR, CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);
    }

    private byte[] convertToJson(Object data) {
        try {
            return jsonExportMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("Error generating JSON", jsonProcessingException);
            throw new RuntimeException("Failed to generate JSON", jsonProcessingException);
        }
    }

    private String formatNumber(double value, String decimalSeparator) {
        String formatted = String.valueOf(value);
        return COMMA_DECIMAL_SEPARATOR.equals(decimalSeparator)
                ? formatted.replace(".", ",")
                : formatted;
    }

}

