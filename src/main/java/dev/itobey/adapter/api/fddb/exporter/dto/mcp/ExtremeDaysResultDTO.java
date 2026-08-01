package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP lookup for the most extreme days of one metric.
 * <p>
 * {@code metric}, {@code direction} and {@code unit} are echoed because the days themselves carry
 * only a bare {@code total} - without them an agent has no way to tell 180 grams of carbs from
 * 180 kcal.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtremeDaysResultDTO {

    private NutrientMetric metric;

    private ExtremeDirection direction;

    /**
     * The unit of the {@code total} of each day: kcal for calories, grams for everything else.
     */
    private String unit;

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of days in this response, which is at most {@code limit}.
     */
    private int resultCount;

    private int limit;

    /**
     * Whether more days exist beyond the cut. The days here are still the most extreme ones, but
     * without this an agent has no way to tell whether the next day was a hair behind the last one
     * returned or nowhere near it - which is what turns a capped list into "these were my heavy
     * days" rather than "the ten heaviest I asked for".
     */
    private boolean truncated;

    /**
     * The days, most extreme first, each with its value for the metric in {@code total}.
     */
    private List<StatsDTO.DayStats> days;
}
