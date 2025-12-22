package dev.itobey.adapter.api.fddb.exporter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains a date range with from and to dates.
 * Used for batch exports and date range queries.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DateRangeDTO {

    @NotNull(message = "From date cannot be null")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "From date must be in the format YYYY-MM-DD")
    private String fromDate;

    @NotNull(message = "To date cannot be null")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "To date must be in the format YYYY-MM-DD")
    private String toDate;

}
