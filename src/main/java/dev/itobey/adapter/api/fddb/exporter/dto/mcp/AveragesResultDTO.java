package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The result of an MCP average over a date range.
 * <p>
 * A range with nothing logged in it is an answer, not an error - the averaging aggregation has
 * nothing to average and would fail, so {@code found} carries that instead, the same way
 * {@link DayResultDTO} carries a day without an entry.
 * <p>
 * {@code loggedDays} next to {@code daysInRange} is the other half of the answer: an average over
 * three logged days out of thirty is a different claim from an average over thirty, and without
 * both numbers the model cannot tell them apart.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AveragesResultDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The length of the range in days, whether logged or not.
     */
    private long daysInRange;

    /**
     * How many of those days have an entry - the number the averages rest on.
     */
    private long loggedDays;

    /**
     * Whether a single day in the range was logged at all.
     */
    private boolean found;

    /**
     * A plain-language explanation, only set when nothing was logged.
     */
    private String message;

    /**
     * The daily averages over the logged days. Null when nothing was logged.
     */
    private StatsDTO.Averages averages;
}
