package dev.itobey.adapter.api.fddb.exporter.ui.util;

import com.vaadin.flow.router.QueryParameters;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** The committed Explorer query, shared by Products and its Entries return link. */
public record ProductExplorerState(String term, LocalDate from, LocalDate to,
                                   Set<DayOfWeek> weekdays, List<String> sort, int row) {
    public ProductExplorerState {
        weekdays = Set.copyOf(weekdays);
        sort = List.copyOf(sort);
    }

    public static ProductExplorerState parse(QueryParameters query) {
        Map<String, List<String>> values = query.getParameters();
        Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (String day : values.getOrDefault("weekday", List.of())) {
            try {
                weekdays.add(DayOfWeek.valueOf(day));
            } catch (IllegalArgumentException ignored) {
                // An obsolete or hand-edited filter must not break the return trip.
            }
        }
        int row = -1;
        try {
            row = Math.max(-1, Integer.parseInt(first(values, "row")));
        } catch (NumberFormatException ignored) {
        }
        return new ProductExplorerState(first(values, "q").trim(), date(values, "from"), date(values, "to"),
                weekdays, values.getOrDefault("sort", List.of()), row);
    }

    public QueryParameters parameters() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("q", List.of(term));
        if (from != null) values.put("from", List.of(from.toString()));
        if (to != null) values.put("to", List.of(to.toString()));
        if (!weekdays.isEmpty()) values.put("weekday", weekdays.stream().sorted().map(Enum::name).toList());
        if (!sort.isEmpty()) values.put("sort", sort);
        if (row >= 0) values.put("row", List.of(String.valueOf(row)));
        return new QueryParameters(values);
    }

    public String productsLocation() {
        return "products?" + parameters().getQueryString();
    }

    private static LocalDate date(Map<String, List<String>> values, String key) {
        String value = first(values, key);
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String first(Map<String, List<String>> values, String key) {
        return values.getOrDefault(key, List.of()).stream().findFirst().orElse("");
    }
}
