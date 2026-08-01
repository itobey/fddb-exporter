package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP keyword day search: the days on which a matching product was logged.
 * <p>
 * Grouped by day rather than returned as a flat occurrence list, because the question this answers
 * is "on which days" - two occurrences of the same food on one day are one day, and counting rows
 * would say otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DaysWithProductsResultDTO {

    private List<String> includeKeywords;

    private List<String> excludeKeywords;

    /**
     * The earliest day considered, absent when the whole diary was searched.
     */
    private LocalDate startDate;

    /**
     * The number of days in {@code days}, i.e. how many were <em>returned</em>. Equal to
     * {@code matchedDayCount} unless {@code truncated} is set.
     */
    private int dayCount;

    /**
     * The number of days that match in total, whether returned or not - the number to answer "on
     * how many days did I eat X?" with. Without it a truncated result cannot answer that question
     * at all, and {@code dayCount} invites a wrong answer.
     */
    private long matchedDayCount;

    /**
     * The number of individual product occurrences across all matching days, not only the returned
     * ones - two portions of one food on one day count twice.
     */
    private long occurrenceCount;

    /**
     * Whether more matching days exist than were returned.
     */
    private boolean truncated;

    /**
     * The matching days, newest first.
     */
    private List<MatchedDay> days;

    /**
     * One day with the distinct names of the products that matched on it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedDay {

        private LocalDate date;

        private List<String> products;
    }
}
