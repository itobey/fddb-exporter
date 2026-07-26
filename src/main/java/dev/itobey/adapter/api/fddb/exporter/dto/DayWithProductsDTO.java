package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * One day on which at least one matching product was logged, with the distinct names that matched.
 * <p>
 * Grouped by the database rather than in memory: the question is "on which days did I eat X", and
 * a broad keyword over years of data matches tens of thousands of individual occurrences that would
 * otherwise all be loaded only to be collapsed into a handful of dates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayWithProductsDTO {

    private LocalDate date;

    /**
     * The distinct matching product names logged on that day, alphabetically.
     */
    private List<String> products;

    /**
     * How many individual occurrences matched on that day - two portions of one food count twice.
     */
    private long occurrences;
}
