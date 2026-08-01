package dev.itobey.adapter.api.fddb.exporter.mcp;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class McpRangeTest {

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);

    @Test
    void of_shouldCountBothBoundsIn() {
        assertEquals(1, McpRange.of(FROM, FROM).days());
        assertEquals(31, McpRange.of(FROM, FROM.plusDays(30)).days());
    }

    @Test
    void of_shouldKeepTheBounds() {
        // when
        McpRange range = McpRange.of(FROM, FROM.plusDays(6));

        // then
        assertEquals(FROM, range.from());
        assertEquals(FROM.plusDays(6), range.to());
    }

    @Test
    void of_shouldRejectAnInvertedRange() {
        // when / then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.of(FROM.plusDays(1), FROM));
        assertEquals(McpRange.INVERTED, exception.getMessage());
    }

    @Test
    void requireOrdered_shouldTreatAMissingBoundAsNoBound() {
        // an optional from/to is how "the whole diary" is expressed, not a mistake
        assertDoesNotThrow(() -> McpRange.requireOrdered(null, null));
        assertDoesNotThrow(() -> McpRange.requireOrdered(FROM, null));
        assertDoesNotThrow(() -> McpRange.requireOrdered(null, FROM));
    }

    @Test
    void requireOrdered_shouldRejectAnInvertedRangeWithTheSameMessageAsOf() {
        // when / then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.requireOrdered(FROM.plusDays(1), FROM));
        assertEquals(McpRange.INVERTED, exception.getMessage());
    }

    @Test
    void capped_shouldAcceptExactlyTheCapAndReturnTheRangeUnchanged() {
        // given: 10 days, both bounds counted
        McpRange range = McpRange.of(FROM, FROM.plusDays(9));

        // when
        McpRange capped = range.capped(10);

        // then
        assertEquals(range, capped);
    }

    @Test
    void capped_shouldRejectOneDayOverAndNameBothNumbers() {
        // when / then: 11 days against a cap of 10
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.of(FROM, FROM.plusDays(10)).capped(10));
        assertTrue(exception.getMessage().contains("10 days"), exception.getMessage());
        assertTrue(exception.getMessage().contains("11 were requested"), exception.getMessage());
    }

    @Test
    void capped_shouldUseTheCallersOwnMessageWhenGivenOne() {
        // when / then: the export tools have to say more than the number
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.of(FROM, FROM.plusDays(20)).capped(14, days -> "no, " + days + " is too many"));
        assertEquals("no, 21 is too many", exception.getMessage());
    }

    @Test
    void notInFuture_shouldAcceptARangeEndingToday() {
        // given
        LocalDate today = LocalDate.of(2024, 6, 1);
        McpRange range = McpRange.of(today.minusDays(3), today);

        // when / then
        assertEquals(range, range.notInFuture(today));
    }

    @Test
    void notInFuture_shouldRejectARangeEndingTomorrowAndNameTheServersToday() {
        // given
        LocalDate today = LocalDate.of(2024, 6, 1);

        // when / then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.of(today, today.plusDays(1)).notInFuture(today));
        assertTrue(exception.getMessage().contains("in the future"), exception.getMessage());
        // naming the server's today is what lets the caller correct itself in one step
        assertTrue(exception.getMessage().contains("2024-06-01"), exception.getMessage());
    }

    @Test
    void checks_shouldChainInTheOrderTheyAreWritten() {
        // given: a range that is both in the future and over the cap
        LocalDate today = LocalDate.of(2024, 6, 1);

        // when / then: whichever is checked first is the one reported, so the order at the call site
        // is the contract - the future is the more actionable of the two and comes first there
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> McpRange.of(today, today.plusDays(30)).notInFuture(today).capped(14));
        assertTrue(exception.getMessage().contains("in the future"), exception.getMessage());
    }
}
