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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualExportService {

    private static final int MIN_DAYS_BACK = 1;
    private static final int MAX_DAYS_BACK = 365;

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;

    public List<FddbData> exportBatch(FddbBatchExport fddbBatchExport) throws ManualExporterException, AuthenticationException {
        LocalDate from = DateUtils.parseDate(fddbBatchExport.getFromDate());
        LocalDate to = DateUtils.parseDate(fddbBatchExport.getToDate());

        if (from.isAfter(to)) {
            throw new ManualExporterException("The 'from' date cannot be after the 'to' date");
        }

        long amountDaysToExport = DAYS.between(from, to) + 1;

        return IntStream.range(0, (int) amountDaysToExport)
                .mapToObj(from::plusDays)
                .map(this::exportForDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private FddbData exportForDate(LocalDate date) {
        Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(date);
        try {
            return exportService.exportDataAndSaveToDb(timeframe);
        } catch (ParseException | AuthenticationException e) {
            log.warn("Data for date {} cannot be parsed or exported, skipping this day", date, e);
            throw new RuntimeException("Error exporting data for date " + date, e);
        }
    }

    public List<FddbData> exportBatchForDaysBack(int days, boolean includeToday) throws ManualExporterException, AuthenticationException {
        if (days < MIN_DAYS_BACK || days > MAX_DAYS_BACK) {
            throw new ManualExporterException("Days back must be between " + MIN_DAYS_BACK + " and " + MAX_DAYS_BACK);
        }

        LocalDate to = includeToday ? LocalDate.now() : LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(days - 1);

        FddbBatchExport timeframe = FddbBatchExport.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build();

        return exportBatch(timeframe);
    }
}