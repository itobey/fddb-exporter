package dev.itobey.adapter.api.fddb.exporter.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains the dates which should be processed in a batch export.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FddbBatchExport {

    @NotNull(message = "From date cannot be null")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "From date must be in the format YYYY-MM-DD")
    private String fromDate;

    @NotNull(message = "To date cannot be null")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "To date must be in the format YYYY-MM-DD")
    private String toDate;

}
