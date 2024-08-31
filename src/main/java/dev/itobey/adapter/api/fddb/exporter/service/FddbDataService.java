package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.ExportRequest;
import dev.itobey.adapter.api.fddb.exporter.domain.ExportResult;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
@RequiredArgsConstructor
@Slf4j
public class FddbDataService {

    @Value("${fddb-exporter.fddb.min-days-back:1}")
    private int minDaysBack;

    @Value("${fddb-exporter.fddb.max-days-back:365}")
    private int maxDaysBack;

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;
    private final PersistenceService persistenceService;

    public List<FddbData> findAllEntries() {
        return persistenceService.findAllEntries();
    }

    public List<FddbData> findByProduct(String name) {
        return persistenceService.findByProduct(name);
    }

    public Optional<FddbData> findByDate(String dateString) {
        LocalDate date = LocalDate.parse(dateString);
        return persistenceService.findByDate(date);
    }

    public ExportResult exportForTimerange(ExportRequest exportRequest) {
        LocalDate from = LocalDate.parse(exportRequest.getFromDate());
        LocalDate to = LocalDate.parse(exportRequest.getToDate());

        if (from.isAfter(to)) {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }

        long amountDaysToExport = DAYS.between(from, to) + 1;

        List<String> successfulDays = new ArrayList<>();
        List<String> unsuccessfulDays = new ArrayList<>();

        IntStream.range(0, (int) amountDaysToExport)
                .mapToObj(from::plusDays)
                .forEach(date -> {
                    try {
                        exportForDate(date);
                        successfulDays.add(date.toString());
                    } catch (ParseException parseException) {
                        unsuccessfulDays.add(date.toString());
                    }
                    // AuthenticationException is not caught and will halt the process
                });

        ExportResult result = new ExportResult();
        result.setSuccessfulDays(successfulDays);
        result.setUnsuccessfulDays(unsuccessfulDays);
        return result;
    }

    public ExportResult exportForDaysBack(int days, boolean includeToday) {
        // safety net to prevent accidents
        if (days < minDaysBack || days > maxDaysBack) {
            throw new DateTimeException("Days back must be between " + minDaysBack + " and " + maxDaysBack);
        }

        LocalDate to = includeToday ? LocalDate.now() : LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(days - 1);

        ExportRequest timeframe = ExportRequest.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build();

        return exportForTimerange(timeframe);
    }

    private void exportForDate(LocalDate date) throws ParseException, AuthenticationException {
        log.debug("exporting data for {}", date);
        Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(date);
        FddbData fddbData = exportService.exportData(timeframe);
        persistenceService.saveOrUpdate(fddbData);
    }
}