package dev.itobey.adapter.api.fddb.exporter.mcp;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the date parameters of the MCP tools.
 * <p>
 * Next to plain ISO dates a few relative aliases are accepted. An agent works from whatever it
 * believes "today" is, which is frequently stale by a day or more; letting the server resolve
 * {@code yesterday} or {@code 13_days_ago} removes both that guess and an extra round-trip. Ranges
 * are expressed by combining two of them, e.g. {@code fromDate=13_days_ago, toDate=today} for the
 * last 14 days.
 */
public final class McpDateParser {

    /**
     * Accepted forms, listed in every error message so the agent can correct itself in one step.
     */
    public static final String ACCEPTED_FORMATS =
            "an ISO date (YYYY-MM-DD), 'today', 'yesterday' or 'N_days_ago' (e.g. '13_days_ago')";

    private static final Pattern DAYS_AGO = Pattern.compile("(\\d{1,5})[_\\- ]?days?[_\\- ]?ago");

    private McpDateParser() {
    }

    /**
     * Resolves a date parameter against the current day.
     *
     * @param value the raw parameter value
     * @return the resolved date
     * @throws DateTimeException if the value is blank or in none of the accepted formats
     */
    public static LocalDate parse(String value) {
        return parse(value, LocalDate.now());
    }

    /**
     * Resolves a date parameter against an explicit "today", so the relative aliases stay testable.
     *
     * @param value the raw parameter value
     * @param today the date the relative aliases are resolved against
     * @return the resolved date
     * @throws DateTimeException if the value is blank or in none of the accepted formats
     */
    public static LocalDate parse(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            throw new DateTimeException("A date is required - pass " + ACCEPTED_FORMATS);
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "today":
                return today;
            case "yesterday":
                return today.minusDays(1);
            default:
                break;
        }

        Matcher daysAgo = DAYS_AGO.matcher(normalized);
        if (daysAgo.matches()) {
            return today.minusDays(Long.parseLong(daysAgo.group(1)));
        }

        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException dateTimeParseException) {
            throw new DateTimeException("'" + value + "' is not a valid date - pass " + ACCEPTED_FORMATS);
        }
    }

    /**
     * Resolves an optional date parameter, treating null and blank as "no bound".
     *
     * @param value the raw parameter value, may be null or blank
     * @return the resolved date, or null if none was given
     * @throws DateTimeException if a non-blank value is in none of the accepted formats
     */
    public static LocalDate parseOptional(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
