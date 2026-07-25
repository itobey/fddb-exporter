package dev.itobey.adapter.api.fddb.exporter.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The result of an MCP lookup for a single day.
 * <p>
 * A day without an entry is a perfectly normal answer ("nothing was logged"), not an error, so it
 * is reported through {@code found} instead of an exception. {@code date} echoes the resolved date,
 * which matters when the caller passed a relative alias such as {@code yesterday}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayResultDTO {

    private LocalDate date;

    private boolean found;

    /**
     * A plain-language explanation, only set when nothing was found.
     */
    private String message;

    private FddbDataDTO entry;
}
