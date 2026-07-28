package dev.itobey.adapter.api.fddb.exporter.mcp;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.function.LongFunction;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * A validated date range and its length in days, both bounds inclusive - the one place the MCP layer
 * decides what an acceptable range is and how it says so.
 * <p>
 * Nearly every tool here takes a from/to pair and has to reject the same three things before touching
 * the store: an inverted range, one longer than the tool can answer, and - for the export tools - one
 * reaching into days that have not happened yet. Written per tool, that produced the same sentence in
 * four places and, worse, an inconsistent one: a tool that validated locally raised a
 * {@link DateTimeException} worded for the agent, while a tool that did not inherited the service
 * layer's {@link IllegalArgumentException} instead. Both reach the client through
 * {@code McpErrorAspect}, so nothing was broken - but which of the two an agent saw depended on which
 * tool it happened to call, and only one of them was written to be acted on.
 * <p>
 * The checks are methods rather than constructor arguments because they do not all apply to every
 * tool, and they return {@code this} so a call site reads as the list of things it does require:
 * {@code McpRange.of(from, to).notInFuture(today).capped(MAX_EXPORT_DAYS, ...)}.
 *
 * @param from the first day, inclusive
 * @param to   the last day, inclusive
 * @param days the number of days the range covers, both bounds counted
 */
record McpRange(LocalDate from, LocalDate to, long days) {

    /**
     * The wording every inverted range is refused with, in the MCP layer and - by coincidence worth
     * keeping - in {@code FddbDataService} and {@code StatsService} too.
     */
    static final String INVERTED = "The 'from' date cannot be after the 'to' date";

    /**
     * A range with both bounds known, rejecting an inverted one.
     *
     * @throws DateTimeException if {@code from} is after {@code to}
     */
    static McpRange of(LocalDate from, LocalDate to) {
        requireOrdered(from, to);
        return new McpRange(from, to, DAYS.between(from, to) + 1);
    }

    /**
     * The inversion check on its own, for the tools whose bounds are both optional and which
     * therefore have no length to compute.
     * <p>
     * A missing bound means "no bound", not a mistake, so a null on either side passes. Those tools
     * would otherwise reach the store and surface its {@code IllegalArgumentException}, which is the
     * asymmetry this type exists to remove.
     *
     * @throws DateTimeException if both bounds are present and {@code from} is after {@code to}
     */
    static void requireOrdered(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DateTimeException(INVERTED);
        }
    }

    /**
     * Rejects a range longer than the caller can answer for.
     *
     * @param maxDays the largest number of days that is still acceptable
     * @return this range, unchanged, when it fits
     * @throws DateTimeException if the range is longer
     */
    McpRange capped(int maxDays) {
        return capped(maxDays, requested -> "The date range must not exceed " + maxDays + " days, but "
                + requested + " were requested - please narrow the range");
    }

    /**
     * The same, for a caller whose refusal has to explain more than the number.
     *
     * @param maxDays the largest number of days that is still acceptable
     * @param message builds the refusal from the number of days that were requested - the export
     *                tools spend real requests to fddb.info per day and have to say where the
     *                uncapped path is, which is not something a generic message can carry
     * @return this range, unchanged, when it fits
     * @throws DateTimeException if the range is longer
     */
    McpRange capped(int maxDays, LongFunction<String> message) {
        if (days > maxDays) {
            throw new DateTimeException(message.apply(days));
        }
        return this;
    }

    /**
     * Rejects a range reaching past the day the server is on.
     * <p>
     * Only the export tools want this. A read tool asked about tomorrow answers {@code found=false}
     * and costs nothing; an export tool asked about tomorrow makes real requests to fddb.info for
     * days that cannot have data. The message names the server's own today, since that is the number
     * the caller got its arithmetic wrong against.
     *
     * @param today the server's current date
     * @return this range, unchanged, when it ends today or earlier
     * @throws DateTimeException if the range ends after {@code today}
     */
    McpRange notInFuture(LocalDate today) {
        if (to.isAfter(today)) {
            throw new DateTimeException("The range ends on " + to + ", which is in the future - today "
                    + "is " + today + " on this server. FDDB cannot have a diary for a day that has "
                    + "not happened yet, so there is nothing to fetch; recompute the range against "
                    + today + " and call again.");
        }
        return this;
    }
}
