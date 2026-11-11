package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v2/body-measurements")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
@Tag(name = "Body Measurements", description = "Import and query body measurements independently of the food diary")
public class BodyMeasurementResourceV2 {
    private final BodyMeasurementStore store;

    @Operation(summary = "List body measurements in chronological order",
            description = "Filter by source and UTC instants. The lower bound is inclusive and the upper bound is exclusive.")
    @GetMapping
    public ResponseEntity<List<BodyMeasurement>> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.find(from, to, source, page, size));
    }

    @Operation(summary = "Import normalized body measurements",
            description = "An existing record with the same source and externalId is replaced.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportResult importMeasurements(@RequestBody List<BodyMeasurement> measurements) {
        return new ImportResult(store.importMeasurements(measurements));
    }

    public record ImportResult(int processed) {
    }
}
