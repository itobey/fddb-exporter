package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
     * @throws AuthenticationException when the authentication is not successful
     * @throws ManualExporterException when the given dates cannot be handled
     */
    public void exportBatch(FddbBatchExport fddbBatchExport) throws ManualExporterException, AuthenticationException {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fddbBatchExport.getFromDate());
            to = LocalDate.parse(fddbBatchExport.getToDate());
        } catch (DateTimeParseException dateTimeParseException) {
            log.error("payload cannot be parsed");
            throw dateTimeParseException;
        }

        if (from.isAfter(to)) {
            throw new ManualExporterException("the 'from' date cannot be after the 'to' date");
        }

        long amountDaysToExport = DAYS.between(from, to) + 1;

        // export days between the given dates
        for (int i = 0; i < amountDaysToExport; i++) {
            Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(from);
            try {
                exportService.exportDataAndSaveToDb(timeframe);
            } catch (ParseException parseException) {
                log.warn("data for date {} cannot be parsed, skipping this day", from);
            }

            from = from.plusDays(1);
        }
    }

    /**
     * Exports the data for yesterday.
     *
     * @throws AuthenticationException when the authentication is not successful
     * @throws ManualExporterException when the given dates cannot be handled
     */
    public void exportBatchForYesterday() throws ManualExporterException, AuthenticationException {
        String yesterday = LocalDate.now().minusDays(1L).format(DateTimeFormatter.ISO_DATE);
        FddbBatchExport yesterdayBatch = FddbBatchExport.builder()
                .fromDate(yesterday)
                .toDate(yesterday)
                .build();
        exportBatch(yesterdayBatch);
    }

}
