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

    /**
     * Upper bound for range queries. Without it a caller could pull years of entries — including
     * their product lists — in a single response.
     */
    public static final int MAX_RANGE_DAYS = 366;

    public List<FddbDataDTO> findAllEntries() {
        List<FddbData> allEntries = persistenceService.findAllEntries();
        return fddbDataMapper.toFddbDataDTO(allEntries);
    }

    public List<ProductWithDateDTO> findByProduct(String name) {
        List<ProductWithDate> productsWithDate = persistenceService.findByProduct(name);
        return fddbDataMapper.toProductWithDateDto(productsWithDate);
    }

    public List<ProductWithDateDTO> findByProduct(String name, List<java.time.DayOfWeek> daysOfWeek) {
        List<ProductWithDate> productsWithDate = persistenceService.findByProduct(name, daysOfWeek);
        return fddbDataMapper.toProductWithDateDto(productsWithDate);
    }

    /**
     * Searches for a product, optionally narrowed down by days of the week, a date range and a
     * maximum number of results.
     */
    public List<ProductWithDateDTO> findByProduct(String name, List<java.time.DayOfWeek> daysOfWeek,
                                                  LocalDate fromDate, LocalDate toDate, Integer limit) {
        validateRange(fromDate, toDate);
        List<ProductWithDate> productsWithDate =
                persistenceService.findByProduct(name, daysOfWeek, fromDate, toDate, limit);
        return fddbDataMapper.toProductWithDateDto(productsWithDate);
    }

    public Optional<FddbDataDTO> findByDate(String dateString) {
        LocalDate date = LocalDate.parse(dateString);
        Optional<FddbData> fddbDataOptional = persistenceService.findByDate(date);
        return fddbDataOptional.map(fddbDataMapper::toFddbDataDTO);
    }

    /**
     * Retrieves all entries in a date range, both bounds inclusive.
     *
     * @param fromDate        the first date to include
     * @param toDate          the last date to include
     * @param includeProducts whether the product lists should be part of the response
     * @return the matching entries ordered by date ascending
     * @throws DateTimeException if the range is inverted or longer than {@link #MAX_RANGE_DAYS}
     */
    public List<FddbDataDTO> findByDateRange(LocalDate fromDate, LocalDate toDate, boolean includeProducts) {
        validateRange(fromDate, toDate);

        long amountDays = DAYS.between(fromDate, toDate) + 1;
        if (amountDays > MAX_RANGE_DAYS) {
            throw new DateTimeException("The date range must not exceed " + MAX_RANGE_DAYS
                    + " days, but " + amountDays + " were requested - please narrow the range");
        }

        List<FddbDataDTO> entries = fddbDataMapper.toFddbDataDTO(persistenceService.findByDateBetween(fromDate, toDate));
        return includeProducts ? entries : fddbDataMapper.toFddbDataDTOWithoutProducts(entries);
    }

    /**
     * Retrieves the entries of the last N days, today included.
     *
     * @param days            how many days to look back
     * @param includeProducts whether the product lists should be part of the response
     * @return the matching entries ordered by date ascending
     * @throws DateTimeException if days is outside 1..{@link #MAX_RANGE_DAYS}
     */
    public List<FddbDataDTO> findRecentDays(int days, boolean includeProducts) {
        if (days < 1 || days > MAX_RANGE_DAYS) {
            throw new DateTimeException("Days must be between 1 and " + MAX_RANGE_DAYS);
        }
        LocalDate toDate = LocalDate.now();
        return findByDateRange(toDate.minusDays(days - 1L), toDate, includeProducts);
    }

    /**
     * Retrieves the most recent entry, which is the cheapest way for a caller to find out how far
     * the exported data reaches.
     *
     * @return an Optional of the newest entry
     */
    public Optional<FddbDataDTO> findLatestEntry() {
        return persistenceService.findLatestEntry().map(fddbDataMapper::toFddbDataDTO);
    }

    public ProductSummaryDTO getProductSummary(String name, LocalDate fromDate, LocalDate toDate) {
        validateRange(fromDate, toDate);
        return persistenceService.getProductSummary(name, fromDate, toDate);
    }

    public List<TopProductDTO> getTopProducts(ProductRanking ranking, LocalDate fromDate, LocalDate toDate, int limit) {
        validateRange(fromDate, toDate);
        return persistenceService.getTopProducts(ranking, fromDate, toDate, limit);
    }

    public List<String> findDistinctProductNames(String search, int limit) {
        return persistenceService.findDistinctProductNames(search, limit);
    }

    private void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }
    }

    public ExportResultDTO exportForTimerange(DateRangeDTO dateRangeDTO) {
        LocalDate from = LocalDate.parse(dateRangeDTO.getFromDate());
        LocalDate to = LocalDate.parse(dateRangeDTO.getToDate());

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

        DateRangeDTO timeframe = DateRangeDTO.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build();

        return exportForTimerange(timeframe);
    }

    public StatsDTO getStats() {
        return statsService.getStats();
    }

    public RollingAveragesDTO getRollingAverages(DateRangeDTO dateRangeDTO) {
        LocalDate fromDate = LocalDate.parse(dateRangeDTO.getFromDate());
        LocalDate toDate = LocalDate.parse(dateRangeDTO.getToDate());

        StatsDTO.Averages averages = statsService.getAveragesForDateRange(fromDate, toDate);
        return RollingAveragesDTO.builder()
                .fromDate(dateRangeDTO.getFromDate())
                .toDate(dateRangeDTO.getToDate())
                .averages(averages)
                .build();
    }

    private void exportForDate(LocalDate date) throws ParseException, AuthenticationException {
        log.debug("exporting data for {}", date);
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeFor(date);
        FddbData fddbData = exportService.exportData(timeframeDTO);
        persistenceService.saveOrUpdate(fddbData);
    }
}