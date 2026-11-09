package dev.itobey.adapter.api.fddb.exporter.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.*;

public final class SamsungHealthCsvParser {
    public static final String SOURCE = "samsung-health";

    private static final Map<String, BodyMetric> METRIC_COLUMNS = Map.of(
            "weight", WEIGHT_KG,
            "body_fat", BODY_FAT_PERCENT,
            "body_fat_mass", BODY_FAT_MASS_KG,
            "total_body_water", BODY_WATER_LITERS,
            "fat_free_mass", FAT_FREE_MASS_KG,
            "skeletal_muscle", SKELETAL_MUSCLE_PERCENT,
            "skeletal_muscle_mass", SKELETAL_MUSCLE_MASS_KG,
            "basal_metabolic_rate", BASAL_METABOLIC_RATE_KCAL_PER_DAY);

    private SamsungHealthCsvParser() {
    }

    public static List<BodyMeasurement> parse(Reader input, int maxRecords) {
        List<BodyMeasurement> measurements = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(input)
                .withCSVParser(new RFC4180ParserBuilder().build()).build()) {
            Map<String, Integer> columns = readHeader(reader);
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length == 1 && row[0].isBlank()) {
                    continue;
                }
                if (measurements.size() == maxRecords) {
                    throw new IllegalArgumentException("Samsung Health CSV exceeds " + maxRecords + " records");
                }
                try {
                    measurements.add(readMeasurement(columns, row));
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    throw new IllegalArgumentException("Invalid Samsung Health CSV record " + reader.getRecordsRead());
                }
            }
        } catch (IOException | CsvValidationException ex) {
            throw new IllegalArgumentException("Could not read Samsung Health CSV");
        }
        return measurements;
    }

    private static Map<String, Integer> readHeader(CSVReader reader) throws IOException, CsvValidationException {
        String[] header = reader.readNext();
        if (header != null && header[0].replace("\uFEFF", "").strip()
                .matches("com\\.samsung\\.s?health\\.(?:weight|body_fat)")) {
            header = reader.readNext();
        }
        Map<String, Integer> columns = new HashMap<>();
        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                String name = header[i].replace("\uFEFF", "").strip();
                name = name.replaceFirst("^com\\.samsung\\.s?health\\.(?:weight|body_fat)\\.", "");
                if (!name.isEmpty() && columns.putIfAbsent(name, i) != null) {
                    throw new IllegalArgumentException("Duplicate Samsung Health CSV header");
                }
            }
        }
        if (!columns.containsKey("datauuid") || !columns.containsKey("start_time")
                || !(columns.containsKey("weight") || columns.containsKey("body_fat"))) {
            throw new IllegalArgumentException("Expected a Samsung Health weight or body fat CSV");
        }
        return columns;
    }

    private static BodyMeasurement readMeasurement(Map<String, Integer> columns, String[] row) {
        Map<BodyMetric, Double> metrics = new EnumMap<>(BodyMetric.class);
        METRIC_COLUMNS.forEach((column, metric) -> {
            String value = cell(columns, row, column);
            if (!value.isEmpty()) {
                metrics.put(metric, Double.valueOf(value));
            }
        });
        return new BodyMeasurement(SOURCE, cell(columns, row, "datauuid"),
                parseTimestamp(cell(columns, row, "start_time")), metrics);
    }

    private static Instant parseTimestamp(String value) {
        if (value.matches("-?[0-9]+")) {
            return Instant.ofEpochMilli(Long.parseLong(value));
        }
        if (value.indexOf(' ') >= 0) {
            // Samsung's exported wall-clock text is already UTC. time_offset describes local display.
            return LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(value);
    }

    private static String cell(Map<String, Integer> columns, String[] row, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            return "";
        }
        if (index >= row.length) {
            throw new IllegalArgumentException("Truncated CSV record");
        }
        return row[index].strip();
    }
}
