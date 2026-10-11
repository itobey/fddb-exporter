package dev.itobey.adapter.api.fddb.exporter.service;

import com.opencsv.CSVWriter;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.DownloadFormat;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductDTO;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Service for downloading FDDB data in various formats (CSV, JSON).
 * Supports downloading all data or data within a specific date range,
 * and can include full product details or just daily totals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataDownloadService {

    /**
     * The largest number of daily entries a single download may cover.
     * <p>
     * A download holds the whole result set in memory more than once — the documents, the DTOs and the
     * serialized bytes — and the endpoint is unauthenticated and repeatable. Rather than let an open-ended
     * request grow with the database, anything above this is refused with a message telling the caller to
     * narrow the range; the range itself is not capped in time, so a larger diary is still fully
     * downloadable in chunks. 365 entries is roughly one year of daily logging.
     */
    public static final int MAX_DOWNLOAD_ENTRIES = 365;

    /**
     * Initial size of the CSV buffer, chosen so a typical download does not spend its time growing the
     * array. Roughly one screenful of rows; it grows from there.
     */
    private static final int INITIAL_CSV_BUFFER_BYTES = 32 * 1024;

    private static final char CSV_SEPARATOR = ';';
    private static final String COMMA_DECIMAL_SEPARATOR = ",";
    private static final String EMPTY_FIELD = "";

    /**
     * Formula prefixes and leading control characters that spreadsheets may interpret as formulas.
     */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

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
    /**
     * The application's single, Spring-configured Jackson mapper - the same one that serializes the REST API,
     * so the download and the API can never drift apart in date format, property naming or null inclusion.
     * Pretty-printing is the one deliberate difference and is applied per write in
     * {@link #convertToJson(Object)}, not on this shared mapper.
     */
    private final ObjectMapper objectMapper;

    /**
     * Downloads data in the specified format.
     *
     * @param fromDate         optional start date (null for all data)
     * @param toDate           optional end date (null for all data)
     * @param format           the download format (CSV or JSON)
     * @param includeProducts  whether to include product details
     * @param decimalSeparator the decimal separator for CSV format (. or ,)
     * @return the data as a byte array in the specified format
     * @throws DownloadTooLargeException if the request would cover more than {@link #MAX_DOWNLOAD_ENTRIES} entries
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
        List<FddbData> entries;
        if (fromDate == null && toDate == null) {
            // count first: the unbounded case is the one that must never be read into memory to find out how big it is
            requireWithinLimit(persistenceService.countAllEntries());
            entries = persistenceService.findAllEntries();
        } else {
            entries = persistenceService.findByDateBetween(fromDate, toDate);
            requireWithinLimit(entries.size());
        }

        List<FddbDataDTO> data = fddbDataMapper.toFddbDataDTO(entries);
        data.sort(Comparator.comparing(FddbDataDTO::getDate));
        log.debug("Fetched {} entries for download", data.size());
        return data;
    }

    private void requireWithinLimit(long entryCount) {
        if (entryCount > MAX_DOWNLOAD_ENTRIES) {
            throw new DownloadTooLargeException(entryCount);
        }
    }

    private byte[] convertTotalsToCsv(List<FddbDataDTO> totals, String decimalSeparator) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(INITIAL_CSV_BUFFER_BYTES);
        try (CSVWriter csvWriter = createCsvWriter(output)) {
            csvWriter.writeNext(TOTALS_CSV_HEADER);

            for (FddbDataDTO total : totals) {
                writeRow(csvWriter, createTotalsRow(total, decimalSeparator));
            }
        } catch (Exception exception) {
            log.error("Error generating CSV for totals", exception);
            throw new DownloadException("Failed to generate CSV", exception);
        }
        return output.toByteArray();
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
        ByteArrayOutputStream output = new ByteArrayOutputStream(INITIAL_CSV_BUFFER_BYTES);
        try (CSVWriter csvWriter = createCsvWriter(output)) {
            csvWriter.writeNext(FULL_DATA_CSV_HEADER);

            for (FddbDataDTO entry : data) {
                writeEntryRows(csvWriter, entry, decimalSeparator);
            }
        } catch (Exception exception) {
            log.error("Error generating CSV for full data", exception);
            throw new DownloadException("Failed to generate CSV", exception);
        }
        return output.toByteArray();
    }

    private void writeEntryRows(CSVWriter csvWriter, FddbDataDTO entry, String decimalSeparator) {
        if (hasProducts(entry)) {
            for (ProductDTO product : entry.getProducts()) {
                writeRow(csvWriter, createProductRow(entry, product, decimalSeparator));
            }
        } else {
            writeRow(csvWriter, createEmptyProductRow(entry, decimalSeparator));
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

    /**
     * Neutralizes formula-like fields in data rows; constant headers bypass this.
     */
    private void writeRow(CSVWriter csvWriter, String[] row) {
        for (int i = 0; i < row.length; i++) {
            row[i] = neutralizeFormula(row[i]);
        }
        csvWriter.writeNext(row);
    }

    /**
     * Prefixes formula-like fields with an apostrophe; CSV quoting alone does not prevent evaluation.
     */
    private String neutralizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /**
     * Writes UTF-8 straight into the byte buffer that is handed back to the caller. Encoding on the way
     * out rather than building a {@code String} first saves one full copy of the whole export.
     */
    private CSVWriter createCsvWriter(OutputStream output) {
        return new CSVWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), CSV_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);
    }

    /**
     * Serializes with the shared mapper but a pretty-printing writer: a downloaded file is meant to be opened and
     * read by a human, while the REST responses that share this mapper stay compact.
     */
    private byte[] convertToJson(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JacksonException jacksonException) {
            log.error("Error generating JSON", jacksonException);
            throw new DownloadException("Failed to generate JSON", jacksonException);
        }
    }

    private String formatNumber(double value, String decimalSeparator) {
        String formatted = String.valueOf(value);
        return COMMA_DECIMAL_SEPARATOR.equals(decimalSeparator)
                ? formatted.replace(".", ",")
                : formatted;
    }

    /**
     * Identifies export serialization failures. The global handler returns HTTP 500.
     */
    public static class DownloadException extends RuntimeException {

        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a download would cover more than {@link #MAX_DOWNLOAD_ENTRIES} entries. Its message is
     * written for a human: the browser shows it verbatim, since the Web UI navigates straight to the
     * download URL rather than fetching it.
     */
    public static class DownloadTooLargeException extends RuntimeException {

        private final long entryCount;

        public DownloadTooLargeException(long entryCount) {
            super("This download would cover " + entryCount + " days, which is more than the maximum of "
                    + MAX_DOWNLOAD_ENTRIES + " days per download. Narrow the range with the fromDate and toDate "
                    + "parameters and download the data in several parts.");
            this.entryCount = entryCount;
        }

        public long getEntryCount() {
            return entryCount;
        }
    }

}

