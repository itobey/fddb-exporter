package dev.itobey.adapter.api.fddb.exporter.util;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Shared date-range validation. {@link DateTimeException} maps to HTTP 400.
 */
public final class DateRanges {

    private DateRanges() {
    }

    /**
     * Rejects inverted ranges; either bound may be null.
     *
     * @param fromDate the first date of the range, or null
     * @param toDate   the last date of the range, or null
     * @throws DateTimeException if both bounds are given and {@code fromDate} is after {@code toDate}
     */
    public static void validate(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new DateTimeException("The 'from' date cannot be after the 'to' date");
        }
    }
}
