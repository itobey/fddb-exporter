package dev.itobey.adapter.api.fddb.exporter.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class McpPageTest {

    @Test
    void fetch_shouldAskForOneMoreThanTheLimit() {
        // given
        AtomicInteger requested = new AtomicInteger();

        // when
        McpPage.fetch(10, max -> {
            requested.set(max);
            return List.of();
        });

        // then
        assertEquals(11, requested.get());
    }

    @Test
    void fetch_shouldReportTheOverflowAndDropTheExtraItem() {
        // given: the store has more than was asked for, so the extra row comes back
        List<String> fetched = List.of("a", "b", "c", "d");

        // when
        McpPage<String> page = McpPage.fetch(3, max -> fetched.subList(0, max));

        // then
        assertTrue(page.truncated());
        assertEquals(List.of("a", "b", "c"), page.items());
        assertEquals(3, page.size());
    }

    @Test
    void fetch_shouldNotReportTruncationWhenTheExtraItemDoesNotExist() {
        // given: exactly the limit is available, so the extra row is not there
        List<String> fetched = List.of("a", "b", "c");

        // when
        McpPage<String> page = McpPage.fetch(3, max -> fetched.subList(0, Math.min(max, fetched.size())));

        // then
        assertFalse(page.truncated());
        assertEquals(fetched, page.items());
    }

    @Test
    void of_shouldCapAnAlreadyCompleteList() {
        // when
        McpPage<Integer> page = McpPage.of(List.of(1, 2, 3, 4, 5), 2);

        // then
        assertTrue(page.truncated());
        assertEquals(List.of(1, 2), page.items());
    }

    @Test
    void of_shouldLeaveAListAtTheLimitAlone() {
        // when
        McpPage<Integer> page = McpPage.of(List.of(1, 2), 2);

        // then
        assertFalse(page.truncated());
        assertEquals(List.of(1, 2), page.items());
    }

    @Test
    void of_shouldHandleAnEmptyList() {
        // when
        McpPage<Integer> page = McpPage.of(List.of(), 10);

        // then
        assertFalse(page.truncated());
        assertEquals(0, page.size());
    }

    @ParameterizedTest
    @CsvSource({
            "5, 5",     // within bounds, taken as given
            "1, 1",
            "50, 20",   // greedier than the maximum
            "0, 10",    // nonsensical, so the default
            "-3, 10"
    })
    void boundedLimit_shouldResolveAgainstTheDefaultAndTheMaximum(Integer limit, int expected) {
        assertEquals(expected, McpPage.boundedLimit(limit, 10, 20));
    }

    @ParameterizedTest
    @NullSource
    void boundedLimit_shouldFallBackToTheDefaultWhenAbsent(Integer limit) {
        assertEquals(10, McpPage.boundedLimit(limit, 10, 20));
    }
}
