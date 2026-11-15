package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.BodyMeasurementDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbWeightReportService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/body-measurements")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
@Tag(name = "Body Measurements", description = "Query stored body measurements independently of the food diary")
public class BodyMeasurementResourceV2 {
    private final BodyMeasurementStore store;
    private final FddbWeightReportService weightReport;

    @Operation(summary = "List body measurements in chronological order",
            description = "The FDDB Weight Report is the only import source. The UTC lower bound is inclusive and the upper bound is exclusive.")
    @GetMapping
    public ResponseEntity<List<BodyMeasurementDTO>> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        var measurements = store.find(from, to, FddbWeightReportService.SOURCE, page, size).stream()
                .map(BodyMeasurementResourceV2::toDTO)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(measurements);
    }

    @Operation(summary = "Get a trend for an FDDB body measurement",
            description = "Groups daily averages by day, ISO week or month. Days without the selected metric are omitted.")
    @GetMapping("/trend")
    public ResponseEntity<List<TrendPointDTO>> getBodyMeasurementTrend(
            @RequestParam BodyMetric metric,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") TrendGranularity granularity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(weightReport.getTrend(metric, fromDate, toDate, granularity));
    }

    private static BodyMeasurementDTO toDTO(BodyMeasurement measurement) {
        var metrics = measurement.metrics().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        return new BodyMeasurementDTO(measurement.source(), measurement.externalId(), measurement.measuredAt(), metrics);
    }
}
