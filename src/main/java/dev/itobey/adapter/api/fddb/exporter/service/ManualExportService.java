package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * The service to handle manual export requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualExportService {

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     * @return a list of the saved or updated data
     * @throws AuthenticationException when the authentication is not successful
     * @throws ManualExporterException when the given dates cannot be handled
     */
    public List<FddbData> exportBatch(FddbBatchExport fddbBatchExport) throws ManualExporterException, AuthenticationException {

        LocalDate from = parseDate(fddbBatchExport.getFromDate());
        LocalDate to = parseDate(fddbBatchExport.getToDate());

        if (from.isAfter(to)) {
            throw new ManualExporterException("the 'from' date cannot be after the 'to' date");
        }

        long amountDaysToExport = DAYS.between(from, to) + 1;

        List<FddbData> savedDataPoints = new ArrayList<>();
        // export days between the given dates
        for (int i = 0; i < amountDaysToExport; i++) {
            Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(from);
            try {
                FddbData fddbData = exportService.exportDataAndSaveToDb(timeframe);
                savedDataPoints.add(fddbData);
            } catch (ParseException parseException) {
                log.warn("data for date {} cannot be parsed, skipping this day", from);
            }
            from = from.plusDays(1);
        }
        return savedDataPoints;
    }

    private LocalDate parseDate(String dateString) throws ManualExporterException {
        try {
            return LocalDate.parse(dateString);
        } catch (DateTimeParseException dateTimeParseException) {
            String errorMsg = "payload of given times cannot be parsed";
            log.error(errorMsg);
            throw new ManualExporterException(errorMsg);
        }
    }

    /**
     * Exports the data for yesterday.
     *
     * @return a list of the saved or updated data
     * @throws AuthenticationException when the authentication is not successful
     * @throws ManualExporterException when the given dates cannot be handled
     */
    public List<FddbData> exportBatchForDaysBack(int days, boolean includeToday) throws ManualExporterException, AuthenticationException {
        String fromDate = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_DATE);
        String toDate = LocalDate.now().minusDays(includeToday ? 0 : 1).format(DateTimeFormatter.ISO_DATE);
        FddbBatchExport timeframe = FddbBatchExport.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        return exportBatch(timeframe);
    }

}
