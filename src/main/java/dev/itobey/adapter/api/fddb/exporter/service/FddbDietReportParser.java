package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class FddbDietReportParser {
    static final String SOURCE = "fddb";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern ID = Pattern.compile("[?&]q=(\\d+)(?:&|$)");
    private static final Pattern VALUE = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|%)?",
            Pattern.CASE_INSENSITIVE);

    private FddbDietReportParser() {
    }

    static List<BodyMeasurement> parse(String html, ZoneId zone) {
        Element table = Jsoup.parse(html == null ? "" : html).selectFirst("table.mydata-history");
        if (table == null) {
            throw new ParseException("FDDB did not return the Diet Report history table. Check that My Data is available.");
        }

        var rows = table.select(":scope > tr, :scope > tbody > tr, :scope > thead > tr");
        var header = rows.isEmpty() ? List.<Element>of() : rows.first().children();
        int date = -1;
        var columns = new EnumMap<BodyMetric, Integer>(BodyMetric.class);
        for (int i = 0; i < header.size(); i++) {
            String label = header.get(i).text().toLowerCase(Locale.ROOT)
                    .replaceFirst("\\s*(?:\\([^)]*\\)|%)$", "").strip();
            switch (label) {
                case "date", "datum" -> date = i;
                case "weight", "gewicht" -> columns.put(BodyMetric.WEIGHT_KG, i);
                case "bodyfat", "body fat", "body fat percentage", "body fat ratio", "fat", "fat ratio", "fat percentage",
                     "körperfett", "körperfettanteil", "fett", "fettanteil" -> columns.put(BodyMetric.BODY_FAT_PERCENT, i);
                default -> { }
            }
        }
        if (date < 0 || columns.isEmpty()) {
            throw new ParseException("FDDB Diet Report is missing the date, weight or body fat columns.");
        }

        List<BodyMeasurement> measurements = new ArrayList<>();
        for (Element row : rows.subList(1, rows.size())) {
            var cells = row.children();
            if (cells.size() == 1 && cells.first().hasAttr("colspan")) {
                continue;
            }
            try {
                var metrics = new EnumMap<BodyMetric, Double>(BodyMetric.class);
                columns.forEach((metric, index) -> addMetric(metrics, metric, cells.get(index).text()));
                if (metrics.isEmpty()) {
                    continue;
                }
                Element edit = row.selectFirst("a[href*=/mydata/][href*=action=edit]");
                var id = ID.matcher(edit == null ? "" : edit.attr("href"));
                if (!id.find()) {
                    throw new IllegalArgumentException();
                }
                LocalDate day = LocalDate.parse(cells.get(date).text(), DATE);
                measurements.add(new BodyMeasurement(SOURCE, id.group(1), day.atStartOfDay(zone).toInstant(), metrics));
            } catch (IllegalArgumentException | IndexOutOfBoundsException | DateTimeParseException ex) {
                throw new ParseException("FDDB Diet Report contains an invalid measurement row.");
            }
        }
        return measurements;
    }

    private static void addMetric(EnumMap<BodyMetric, Double> metrics, BodyMetric metric, String value) {
        if (value.isBlank() || value.matches("[-–—]+")) {
            return;
        }
        var match = VALUE.matcher(value);
        String unit = metric == BodyMetric.WEIGHT_KG ? "kg" : "%";
        if (!match.matches() || (match.group(2) != null && !unit.equalsIgnoreCase(match.group(2)))) {
            throw new IllegalArgumentException();
        }
        metrics.put(metric, Double.parseDouble(match.group(1).replace(',', '.')));
    }
}
