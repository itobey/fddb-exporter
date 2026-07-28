package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.mcp.ExportSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * The MCP tools that write: they log into fddb.info with the configured account, scrape the diary
 * for a set of days and store what they find.
 * <p>
 * A different risk class from every other tool here, and treated as one. They are registered only
 * when {@code fddb-exporter.mcp.write-tools-enabled} is set on top of the two properties the read
 * tools need, so the out-of-the-box server is read-only and an agent connected to it cannot see
 * these tools at all, let alone call them. The flag is a bean condition, so turning it on takes a
 * restart - the right granularity for "may this server write".
 * <p>
 * MongoDB is part of that condition even though exporting itself would work with InfluxDB alone.
 * {@code export_missing_days} needs the Mongo gap list, and a server that can scrape but cannot
 * answer a single question about the result is not a configuration worth supporting.
 * <p>
 * Only one export runs at a time, application-wide - the lock lives in {@code FddbDataService}, so
 * these tools, the REST API and the nightly scheduler all queue behind the same one. A second
 * caller is refused immediately rather than made to wait, since an agent handles "try again later"
 * far better than a call that hangs.
 * <p>
 * Every call is capped at {@value #MAX_EXPORT_DAYS} days, which is deliberately far below what the
 * REST API and the Web UI allow. Those are driven by a person who can watch a long run finish; a
 * tool call is driven by a client that gives up after a fixed timeout and by an agent that has no
 * sense of what a call costs. The refusal names the uncapped paths so the agent can hand a real
 * backfill back to the user instead of issuing the same call fourteen days at a time.
 * <p>
 * A range reaching into the future is refused rather than scraped. The read tools accept any date
 * the parser understands and answer {@code found=false} for a day that cannot have data; here the
 * same mistake costs real requests to fddb.info out of the per-call budget. {@code export_days_back}
 * needs no such check - it counts back from today or yesterday and cannot address a future day.
 * <p>
 * Both underlying export methods swallow a parse failure per day and report it as an unsuccessful
 * day, which is also what a day with nothing logged on FDDB looks like. An authentication failure
 * is not swallowed and aborts the whole call - the credentials being wrong is not something to
 * report per day.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = {"fddb-exporter.mcp.enabled", "fddb-exporter.persistence.mongodb.enabled",
                "fddb-exporter.mcp.write-tools-enabled"},
        havingValue = "true")
public class FddbExportTools {

    /**
     * Upper bound for a single export, and the one place the number lives - every tool description
     * and error message below reads it from here.
     * <p>
     * Every day is one sequential HTTP round-trip to fddb.info, so this is two budgets at once: the
     * load a tool call puts on a third-party site, and the latency of the call itself. At roughly a
     * second per day the second budget is the tighter one - MCP clients time out well before a
     * 90-day run finishes, and the client giving up while the server keeps scraping is the worst of
     * both worlds. Fourteen days stays comfortably inside a typical client timeout and covers the
     * realistic ask ("catch me up"); a bigger backfill belongs in the Web UI or the REST API, where
     * nothing is waiting on a response.
     * <p>
     * Package-private rather than private so {@link FddbPrompts} can state the same number: a
     * workflow that ends in "now export the gaps" has to know how many gaps that can cover.
     */
    static final int MAX_EXPORT_DAYS = 14;

    /**
     * Appended to every refusal. An agent that is only told "no" tries a slightly smaller range;
     * one that is told where the unbounded path is can hand the job back to the user instead.
     */
    private static final String BIGGER_EXPORTS = "For a larger backfill use the app's Web UI or its "
            + "REST API (POST /api/v2/fddbdata), neither of which is capped this way - tell the user "
            + "to run it there rather than issuing many calls here.";

    private final FddbDataService fddbDataService;

    @McpTool(
            name = "export_range",
            description = """
                    Fetches the diary from fddb.info for a date range and stores it. Existing \
                    entries are updated rather than duplicated, so re-exporting a day is safe. A day \
                    that comes back as unsuccessful usually just has nothing logged on FDDB - it is \
                    not an error, and the stored data for it is left untouched. At most 14 days per \
                    call - a larger backfill belongs in the app's uncapped Web UI or REST API (POST \
                    /api/v2/fddbdata), not in repeated calls here.""",
            annotations = @McpTool.McpAnnotations(destructiveHint = false,
                    idempotentHint = true))
    public ExportSummaryDTO exportRange(
            @McpToolParam(description = "First day to export: " + McpDateParser.ACCEPTED_FORMATS)
            String fromDate,

            @McpToolParam(description = "Last day to export: " + McpDateParser.ACCEPTED_FORMATS)
            String toDate) {
        LocalDate today = LocalDate.now();
        LocalDate from = McpDateParser.parse(fromDate, today);
        LocalDate to = McpDateParser.parse(toDate, today);
        long days = daysIn(from, to, today);
        log.info("MCP: exporting {} to {}", from, to);

        return summarize(from, to, days, exportForTimerange(from, to));
    }

    @McpTool(
            name = "export_days_back",
            description = """
                    Fetches the diary from fddb.info for the last N days and stores it - the quick \
                    way to catch up without working out dates. Without includeToday the range ends \
                    yesterday, which is usually what is wanted: a day still in progress is \
                    incomplete on FDDB too. Existing entries are updated rather than duplicated. \
                    Between 1 and 14 days per call - a larger backfill belongs in the app's uncapped \
                    Web UI or REST API (GET /api/v2/fddbdata/export), not in repeated calls \
                    here.""",
            annotations = @McpTool.McpAnnotations(destructiveHint = false,
                    idempotentHint = true))
    public ExportSummaryDTO exportDaysBack(
            @McpToolParam(description = "How many days to export, counting back from the last day "
                    + "of the range")
            int days,

            @McpToolParam(description = "End the range today instead of yesterday. Defaults to "
                    + "false", required = false)
            Boolean includeToday) {
        boolean withToday = Boolean.TRUE.equals(includeToday);
        // both bounds are checked here rather than left to the service, whose own message names the
        // configured 1-365 window - a range this tool does not have and an agent cannot act on
        if (days < 1) {
            throw new DateTimeException("An export covers at least one day, but " + days
                    + " were requested - pass how many days to count back from the end of the "
                    + "range, e.g. 7 for the last week.");
        }
        if (days > MAX_EXPORT_DAYS) {
            throw new DateTimeException(exportTooLargeMessage(days));
        }
        log.info("MCP: exporting the last {} days (includeToday={})", days, withToday);

        // the end of the range is resolved once, here, and handed to the service - reading the clock
        // again after the export would report a range shifted by a day for a run that crossed midnight
        LocalDate to = withToday ? LocalDate.now() : LocalDate.now().minusDays(1);
        ExportResultDTO result = fddbDataService.exportForDaysBack(days, to);

        return summarize(to.minusDays(days - 1L), to, days, result);
    }

    @McpTool(
            name = "export_missing_days",
            description = """
                    Fetches only the days in a range that have no usable entry yet and stores them - \
                    the repair tool for the gaps list_missing_days reports. Days that are already \
                    logged are not touched and not re-fetched. If a day stays missing after this, \
                    nothing was logged on FDDB for it, and no number of retries will change that. At \
                    most 14 missing days per call, however long the range itself is - more gaps than \
                    that belong in the app's uncapped Web UI or REST API (POST /api/v2/fddbdata), \
                    not in repeated calls here.""",
            annotations = @McpTool.McpAnnotations(destructiveHint = false,
                    idempotentHint = true))
    public ExportSummaryDTO exportMissingDays(
            @McpToolParam(description = "First day to check: " + McpDateParser.ACCEPTED_FORMATS)
            String fromDate,

            @McpToolParam(description = "Last day to check: " + McpDateParser.ACCEPTED_FORMATS)
            String toDate) {
        LocalDate today = LocalDate.now();
        LocalDate from = McpDateParser.parse(fromDate, today);
        LocalDate to = McpDateParser.parse(toDate, today);
        long daysInRange = validRangeDays(from, to, today);

        // the range itself is only read, so it is not capped - what is capped is the scraping below
        List<LocalDate> missingDays = fddbDataService.getMissingDays(from, to);
        if (missingDays.isEmpty()) {
            return ExportSummaryDTO.builder()
                    .fromDate(from)
                    .toDate(to)
                    .daysRequested(daysInRange)
                    .successCount(0)
                    .failureCount(0)
                    .successfulDays(List.of())
                    .unsuccessfulDays(List.of())
                    .message("Every day from " + from + " to " + to + " already has an entry - "
                            + "nothing was exported.")
                    .build();
        }
        if (missingDays.size() > MAX_EXPORT_DAYS) {
            throw new DateTimeException(exportTooLargeMessage(missingDays.size())
                    + " Narrowing the range works too: " + from + " to " + to + " has "
                    + missingDays.size() + " days without an entry.");
        }
        log.info("MCP: exporting the {} missing days between {} and {}", missingDays.size(), from, to);

        // one call per gap rather than one call over the whole range, so logged days are left alone -
        // held under one lock, or another caller could slip in between two gaps
        ExportResultDTO result = fddbDataService.withExportLock(() -> {
            ExportResultDTO gaps = new ExportResultDTO(new ArrayList<>(), new ArrayList<>());
            for (LocalDate missingDay : missingDays) {
                ExportResultDTO dayResult = exportForTimerange(missingDay, missingDay);
                gaps.getSuccessfulDays().addAll(dayResult.getSuccessfulDays());
                gaps.getUnsuccessfulDays().addAll(dayResult.getUnsuccessfulDays());
            }
            return gaps;
        });

        return summarize(from, to, missingDays.size(), result);
    }

    private ExportResultDTO exportForTimerange(LocalDate from, LocalDate to) {
        return fddbDataService.exportForTimerange(DateRangeDTO.builder()
                .fromDate(from.toString())
                .toDate(to.toString())
                .build());
    }

    /**
     * The length of a range that may be scraped: not inverted, not reaching into the future.
     * <p>
     * The future check is here rather than in {@link McpDateParser} because it is only wrong for
     * these tools. A read tool asked about tomorrow answers {@code found=false} and costs nothing;
     * an export tool asked about tomorrow makes real requests to fddb.info for days that cannot have
     * data, and spends the per-call budget on them. The message names the server's own today, since
     * that is the number the agent got its arithmetic wrong against.
     */
    private long validRangeDays(LocalDate from, LocalDate to, LocalDate today) {
        if (from.isAfter(to)) {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }
        if (to.isAfter(today)) {
            throw new DateTimeException("The range ends on " + to + ", which is in the future - "
                    + "today is " + today + " on this server. FDDB cannot have a diary for a day "
                    + "that has not happened yet, so there is nothing to export; recompute the range "
                    + "against " + today + " and call again.");
        }
        return DAYS.between(from, to) + 1;
    }

    /**
     * The same, plus the per-call cap. Separate from {@link #validRangeDays} because
     * {@code export_missing_days} caps the gaps it scrapes, not the range it reads.
     */
    private long daysIn(LocalDate from, LocalDate to, LocalDate today) {
        long days = validRangeDays(from, to, today);
        if (days > MAX_EXPORT_DAYS) {
            throw new DateTimeException(exportTooLargeMessage(days));
        }
        return days;
    }

    private String exportTooLargeMessage(long days) {
        return "An export covers at most " + MAX_EXPORT_DAYS + " days per call, but " + days
                + " were requested - every day is a separate request to fddb.info and takes about a "
                + "second, so a longer run would outlast this call. " + BIGGER_EXPORTS;
    }

    private ExportSummaryDTO summarize(LocalDate from, LocalDate to, long daysRequested, ExportResultDTO result) {
        List<String> successful = result.getSuccessfulDays();
        List<String> unsuccessful = result.getUnsuccessfulDays();

        return ExportSummaryDTO.builder()
                .fromDate(from)
                .toDate(to)
                .daysRequested(daysRequested)
                .successCount(successful.size())
                .failureCount(unsuccessful.size())
                .successfulDays(successful)
                .unsuccessfulDays(unsuccessful)
                .message(messageFor(successful.size(), unsuccessful.size()))
                .build();
    }

    private String messageFor(int successCount, int failureCount) {
        if (failureCount == 0) {
            return "Exported and stored " + successCount + " day(s).";
        }
        return "Exported and stored " + successCount + " day(s); " + failureCount + " day(s) returned "
                + "nothing usable from FDDB, which normally means nothing was logged on them. Their "
                + "stored data, if any, is unchanged.";
    }
}
