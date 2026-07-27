package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ExportInProgressException;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
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
     * Guards every scraping run in this application against every other one: the scheduler, the
     * REST API (and through it the Web UI) and the MCP export tools all pass through here.
     * <p>
     * Before MCP there was one automated caller and a human; an agent has no sense of what a call
     * costs and will happily start three at once, or start one while the nightly cron is running.
     * Concurrent runs each log into fddb.info under the same account and write to the same days -
     * duplicate load and interleaved writes for no gain. Reentrant on purpose, so an export that
     * internally delegates to another export method does not deadlock itself.
     * <p>
     * In-process only. This is a single-instance, self-hosted application; two containers pointed
     * at one FDDB account would still overlap, and nothing here pretends otherwise.
     */
    private final ReentrantLock exportLock = new ReentrantLock();

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

    /**
     * Finds the most recent days on which a product matching at least one of the include keywords
     * and none of the exclude ones was logged - "on which days did I eat X but not Y".
     * <p>
     * Grouping and capping both happen in the database. Returning the individual occurrences and
     * collapsing them here would mean loading every match in the diary for a broad keyword, only to
     * discard almost all of them.
     *
     * @param includeKeywords case-insensitive substrings of the product name, at least one must match
     * @param excludeKeywords case-insensitive substrings that disqualify an occurrence, may be empty
     * @param startDate       the earliest date to consider, or null for the whole diary
     * @param limit           the maximum number of days to return
     * @return the matching days, newest first
     */
    public List<DayWithProductsDTO> findDaysWithProducts(List<String> includeKeywords, List<String> excludeKeywords,
                                                         LocalDate startDate, int limit) {
        return persistenceService.findDaysWithProducts(
                includeKeywords == null ? List.of() : includeKeywords,
                excludeKeywords == null ? List.of() : excludeKeywords,
                startDate,
                limit);
    }

    /**
     * Counts what the same search matches in total, for a caller that returned only part of it.
     *
     * @param includeKeywords case-insensitive substrings of the product name, at least one must match
     * @param excludeKeywords case-insensitive substrings that disqualify an occurrence, may be empty
     * @param startDate       the earliest date to consider, or null for the whole diary
     * @return the total number of matching days and occurrences
     */
    public ProductDayTotalsDTO countDaysWithProducts(List<String> includeKeywords, List<String> excludeKeywords,
                                                     LocalDate startDate) {
        return persistenceService.countDaysWithProducts(
                includeKeywords == null ? List.of() : includeKeywords,
                excludeKeywords == null ? List.of() : excludeKeywords,
                startDate);
    }

    public List<StatsDTO.DayStats> getExtremeDays(NutrientMetric metric, ExtremeDirection direction, int limit,
                                                  LocalDate fromDate, LocalDate toDate) {
        return statsService.getExtremeDays(metric, direction, limit, fromDate, toDate);
    }

    public List<TrendPointDTO> getTrend(NutrientMetric metric, LocalDate fromDate, LocalDate toDate,
                                        TrendGranularity granularity) {
        return statsService.getTrend(metric, fromDate, toDate, granularity);
    }

    public List<WeekdayStatsDTO> getWeekdayBreakdown(LocalDate fromDate, LocalDate toDate) {
        return statsService.getWeekdayBreakdown(fromDate, toDate);
    }

    public MacroSplitDTO getMacroSplit(LocalDate fromDate, LocalDate toDate) {
        return statsService.getMacroSplit(fromDate, toDate);
    }

    public List<LocalDate> getMissingDays(LocalDate fromDate, LocalDate toDate) {
        return statsService.getMissingDays(fromDate, toDate);
    }

    /**
     * Counts the days in a range that have an entry, without loading them.
     *
     * @param fromDate the first date to count, or null for no lower bound
     * @param toDate   the last date to count, or null for no upper bound
     * @return the number of logged days in the range
     */
    public long countByDateRange(LocalDate fromDate, LocalDate toDate) {
        return statsService.countByDateRange(fromDate, toDate);
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

        return withExportLock(() -> {
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
        });
    }

    public ExportResultDTO exportForDaysBack(int days, boolean includeToday) {
        return exportForDaysBack(days, includeToday ? LocalDate.now() : LocalDate.now().minusDays(1));
    }

    /**
     * Exports the last {@code days} days ending on an explicitly given day.
     * <p>
     * Exists so a caller that has to report the range it exported can resolve the end date itself
     * and pass the same value in. Letting this method read the clock leaves the caller reading it a
     * second time after the export has run, and a run that crosses midnight then gets reported
     * against a range one day off the one it actually fetched.
     *
     * @param days the number of days to export, counting back from and including {@code toDate}
     * @param toDate the last day of the range
     * @return which days were exported successfully and which came back empty
     * @throws DateTimeException if {@code days} is outside the configured min/max window
     */
    public ExportResultDTO exportForDaysBack(int days, LocalDate toDate) {
        // safety net to prevent accidents
        int maxDaysBack = properties.getFddb().getMaxDaysBack();
        int minDaysBack = properties.getFddb().getMinDaysBack();
        if (days < minDaysBack || days > maxDaysBack) {
            throw new DateTimeException("Days back must be between " + minDaysBack + " and " + maxDaysBack);
        }

        LocalDate from = toDate.minusDays(days - 1);

        DateRangeDTO timeframe = DateRangeDTO.builder()
                .fromDate(from.toString())
                .toDate(toDate.toString())
                .build();

        return exportForTimerange(timeframe);
    }

    public StatsDTO getStats() {
        return statsService.getStats();
    }

    /**
     * Returns only the period the diary covers, without computing the rest of {@link #getStats()}.
     *
     * @return the first and last logged day and how many days have an entry
     */
    public CoverageWindowDTO getCoverageWindow() {
        return statsService.getCoverageWindow();
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

    /**
     * Runs an action as the only export in this application, or refuses it.
     * <p>
     * Public because a caller that scrapes in several steps - {@code export_missing_days} fetches
     * one day per gap - has to hold the lock across all of them, not re-take it between days.
     * Nested calls from the same thread are free, so such a caller can wrap a loop that itself
     * calls {@link #exportForTimerange}.
     *
     * @param export the scraping run to perform
     * @return whatever the run returns
     * @throws ExportInProgressException if another export is already running
     */
    public <T> T withExportLock(Supplier<T> export) {
        if (!exportLock.tryLock()) {
            throw new ExportInProgressException("An export is already running - wait for it to "
                    + "finish before starting another. Exports scrape fddb.info one day at a time, "
                    + "so a large one can take a while.");
        }
        try {
            return export.get();
        } finally {
            exportLock.unlock();
        }
    }

    private void exportForDate(LocalDate date) throws ParseException, AuthenticationException {
        log.debug("exporting data for {}", date);
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeFor(date);
        FddbData fddbData = exportService.exportData(timeframeDTO);
        persistenceService.saveOrUpdate(fddbData);
    }
}