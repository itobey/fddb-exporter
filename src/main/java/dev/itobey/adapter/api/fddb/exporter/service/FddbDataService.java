package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final TimeframeCalculator timeframeCalculator;
    private final ExportService exportService;
    private final PersistenceService persistenceService;
    private final FddbDataMapper fddbDataMapper;
    private final StatsService statsService;
    private final FddbExporterProperties properties;

    public List<FddbDataDTO> findAllEntries() {
        List<FddbData> allEntries = persistenceService.findAllEntries();
        return fddbDataMapper.toFddbDataDTO(allEntries);
    }

    public List<ProductWithDateDTO> findByProduct(String name) {
        List<ProductWithDate> productsWithDate = persistenceService.findByProduct(name);
        return fddbDataMapper.toProductWithDateDto(productsWithDate);
    }

    public Optional<FddbDataDTO> findByDate(String dateString) {
        LocalDate date = LocalDate.parse(dateString);
        Optional<FddbData> fddbDataOptional = persistenceService.findByDate(date);
        return fddbDataOptional.map(fddbDataMapper::toFddbDataDTO);
    }

    public ExportResultDTO exportForTimerange(ExportRequestDTO exportRequestDTO) {
        LocalDate from = LocalDate.parse(exportRequestDTO.getFromDate());
        LocalDate to = LocalDate.parse(exportRequestDTO.getToDate());

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

        ExportResultDTO result = new ExportResultDTO();
        result.setSuccessfulDays(successfulDays);
        result.setUnsuccessfulDays(unsuccessfulDays);
        return result;
    }

    public ExportResultDTO exportForDaysBack(int days, boolean includeToday) {
        // safety net to prevent accidents
        int maxDaysBack = properties.getFddb().getMaxDaysBack();
        int minDaysBack = properties.getFddb().getMinDaysBack();
        if (days < minDaysBack || days > maxDaysBack) {
            throw new DateTimeException("Days back must be between " + minDaysBack + " and " + maxDaysBack);
        }

        LocalDate to = includeToday ? LocalDate.now() : LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(days - 1);

        ExportRequestDTO timeframe = ExportRequestDTO.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build();

        return exportForTimerange(timeframe);
    }

    public StatsDTO getStats() {
        return statsService.getStats();
    }

    private void exportForDate(LocalDate date) throws ParseException, AuthenticationException {
        log.debug("exporting data for {}", date);
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeFor(date);
        FddbData fddbData = exportService.exportData(timeframeDTO);
        persistenceService.saveOrUpdate(fddbData);
    }
}