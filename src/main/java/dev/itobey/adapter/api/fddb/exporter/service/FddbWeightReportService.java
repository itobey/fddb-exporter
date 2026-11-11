package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbApi;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import dev.itobey.adapter.api.fddb.exporter.util.DateRanges;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class FddbWeightReportService {
    private final FddbApi fddbApi;
    private final BodyMeasurementStore store;
    private final FddbExporterProperties properties;

    int sync() {
        var measurements = FddbWeightReportParser.parse(fddbApi.getWeightReport(), properties.resolveZone());
        return measurements.isEmpty() ? 0 : store.importMeasurements(measurements);
    }

    void syncAlongsideDiary() {
        try {
            sync();
        } catch (RuntimeException ex) {
            log.warn("Could not sync the FDDB Weight Report ({})", ex.getClass().getSimpleName());
        }
    }

    public List<TrendPointDTO> getTrend(BodyMetric metric, LocalDate from, LocalDate to,
                                        TrendGranularity granularity) {
        DateRanges.validate(from, to);
        var zone = properties.resolveZone();
        var readings = store.findHistory(from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(), FddbWeightReportParser.SOURCE);

        // Give each measured day equal weight even when FDDB has several readings for that day.
        var days = new LinkedHashMap<LocalDate, DoubleSummaryStatistics>();
        for (var reading : readings) {
            Double value = reading.metrics().get(metric);
            if (value != null) {
                LocalDate date = reading.measuredAt().atZone(zone).toLocalDate();
                days.computeIfAbsent(date, key -> new DoubleSummaryStatistics()).accept(value);
            }
        }

        var buckets = new LinkedHashMap<String, List<LocalDate>>();
        days.keySet().forEach(date -> buckets.computeIfAbsent(granularity.bucketLabel(date),
                key -> new ArrayList<>()).add(date));
        return buckets.entrySet().stream().map(bucket -> {
            var dates = bucket.getValue();
            var values = dates.stream().mapToDouble(date -> days.get(date).getAverage()).summaryStatistics();
            return TrendPointDTO.builder().bucket(bucket.getKey())
                    .fromDate(dates.getFirst()).toDate(dates.getLast())
                    .dayCount(values.getCount()).average(values.getAverage()).total(values.getSum()).build();
        }).toList();
    }
}
