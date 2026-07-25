package dev.itobey.adapter.api.fddb.exporter.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class McpDateParserTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 12, 22);

    @Test
    void parse_shouldAcceptIsoDates() {
        // when
        LocalDate result = McpDateParser.parse("2024-01-31", TODAY);

        // then
        assertEquals(LocalDate.of(2024, 1, 31), result);
    }

    @ParameterizedTest
    @CsvSource({
            "today, 2024-12-22",
            "TODAY, 2024-12-22",
            " yesterday , 2024-12-21",
            "7_days_ago, 2024-12-15",
            "1_day_ago, 2024-12-21",
            "13-days-ago, 2024-12-09",
            "0_days_ago, 2024-12-22"
    })
    void parse_shouldResolveRelativeAliases(String value, LocalDate expected) {
        // when
        LocalDate result = McpDateParser.parse(value, TODAY);

        // then
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "tomorrow", "22.12.2024", "last_week", "2024-13-01", "days_ago"})
    void parse_shouldRejectAnythingElseWithAHelpfulMessage(String value) {
        // when
        DateTimeException exception =
                assertThrows(DateTimeException.class, () -> McpDateParser.parse(value, TODAY));

        // then
        assertTrue(exception.getMessage().contains(McpDateParser.ACCEPTED_FORMATS));
    }

    @Test
    void parse_shouldRejectNull() {
        // when / then
        assertThrows(DateTimeException.class, () -> McpDateParser.parse(null, TODAY));
    }

    @Test
    void parseOptional_shouldTreatMissingValuesAsNoBound() {
        // when / then
        assertNull(McpDateParser.parseOptional(null));
        assertNull(McpDateParser.parseOptional("  "));
        assertEquals(LocalDate.of(2024, 1, 31), McpDateParser.parseOptional("2024-01-31"));
    }
}
