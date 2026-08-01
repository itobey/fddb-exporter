package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * The window the diary covers: how many days have an entry, and the first and last of them.
 * <p>
 * The cheap subset of {@link StatsDTO} for a caller that only needs to know what period the data
 * spans. Three indexed reads rather than the extremes, streaks and product counts a full stats
 * call computes over the whole collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageWindowDTO {

    /**
     * The first day the diary holds an entry for, null when it is empty.
     */
    private LocalDate firstEntryDate;

    /**
     * The most recent day the diary holds an entry for, null when it is empty.
     */
    private LocalDate lastEntryDate;

    /**
     * How many days have an entry.
     */
    private long entryCount;
}
