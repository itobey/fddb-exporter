package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How much a product keyword search matches in total: days and individual occurrences.
 * <p>
 * Counted in the database, so a caller that returns only the first page of days can still say how
 * many there are without fetching the rest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDayTotalsDTO {

    /**
     * The number of distinct days with at least one match.
     */
    private long dayCount;

    /**
     * The number of individual matching occurrences across those days.
     */
    private long occurrenceCount;
}
