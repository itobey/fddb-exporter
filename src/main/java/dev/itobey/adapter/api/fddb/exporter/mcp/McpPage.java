package dev.itobey.adapter.api.fddb.exporter.mcp;

import java.util.List;
import java.util.function.IntFunction;

/**
 * A capped slice of a tool result together with the flag that says whether anything was left out,
 * and the limit arithmetic that produces one.
 * <p>
 * Every list-returning tool in this package bounds its response the same way: an optional
 * {@code limit} parameter is resolved against a default and a maximum, the store is asked for one
 * row <em>more</em> than that, and the extra row - if it comes back - is dropped and reported as
 * {@code truncated}. Asking for one extra is what makes the flag possible without a second counting
 * query; the cost is that the {@code + 1} and the cut back down have to agree, and a call site that
 * owns both can get them out of step. A tool that reports {@code truncated=false} over a truncated
 * list is worse than one with no flag at all, since the count derived from it is then confidently
 * wrong.
 * <p>
 * {@link #fetch} therefore owns both halves. The per-tool defaults and maxima stay with their tools,
 * where the reasoning about response size belongs - only the arithmetic lives here.
 *
 * @param items     the items to return, at most {@code limit} of them
 * @param truncated whether more items existed than were returned
 */
record McpPage<T>(List<T> items, boolean truncated) {

    /**
     * Asks for one item more than wanted, so an overflow can be reported instead of silently
     * truncating.
     *
     * @param limit   how many items the tool will return
     * @param fetcher retrieves at most the number of items it is handed - it takes that number
     *                rather than closing over {@code limit} precisely so it cannot use the
     *                unincremented one
     */
    static <T> McpPage<T> fetch(int limit, IntFunction<List<T>> fetcher) {
        return of(fetcher.apply(limit + 1), limit);
    }

    /**
     * Caps a list that is already complete - for the callers that need the full one anyway, either
     * because their counts are computed from it or because the query cannot be limited.
     */
    static <T> McpPage<T> of(List<T> items, int limit) {
        boolean truncated = items.size() > limit;
        return new McpPage<>(truncated ? items.subList(0, limit) : items, truncated);
    }

    /**
     * The effective value of an optional {@code limit} tool parameter: the default when it is
     * absent or nonsensical, the maximum when it is greedier than that. Deliberately never throws -
     * a limit is a hint about response size, and failing the call over one would cost the agent a
     * round trip to learn what the tool description already states.
     */
    static int boundedLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    int size() {
        return items.size();
    }
}
