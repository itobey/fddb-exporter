package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP gap check: the days in a range that were never logged.
 * <p>
 * The counts are given next to the list so an agent can answer "how well did I log last month?"
 * without counting array elements itself. They stay accurate when the list is cut: a multi-year
 * audit of a sparse diary would otherwise return thousands of bare date strings, and the count is
 * the part of that answer worth reading anyway.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingDaysResultDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of days the range spans, both bounds inclusive.
     */
    private long daysChecked;

    /**
     * The number of days in the range without a usable entry.
     */
    private int missingCount;

    /**
     * The number of days in the range with a usable entry, i.e. {@code daysChecked - missingCount}.
     */
    private long loggedCount;

    /**
     * Whether more days are missing than {@code missingDays} lists. {@code missingCount} stays the
     * true total either way, so a "how many did I miss?" answer is never wrong - only the list of
     * dates is cut.
     */
    private boolean truncated;

    /**
     * The upper bound on how many dates are listed, only set when the list was cut.
     */
    private Integer limit;

    /**
     * The missing days in chronological order, oldest first, at most {@code limit} of them.
     */
    private List<LocalDate> missingDays;
}
