package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of an MCP export: which days were scraped from fddb.info and stored, and which were not.
 * <p>
 * A failed day is the normal outcome for a day with nothing logged on FDDB, not an error - the
 * {@code message} says so, because "3 of 5 days failed" invites an agent to report a problem that
 * is really just an empty diary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportSummaryDTO {

    private LocalDate fromDate;

    private LocalDate toDate;

    /**
     * The number of days the export covered, both bounds inclusive.
     */
    private long daysRequested;

    /**
     * The number of days that were scraped and stored.
     */
    private int successCount;

    /**
     * The number of days FDDB returned nothing usable for.
     */
    private int failureCount;

    private List<String> successfulDays;

    private List<String> unsuccessfulDays;

    /**
     * A plain-language reading of the counts above.
     */
    private String message;
}
