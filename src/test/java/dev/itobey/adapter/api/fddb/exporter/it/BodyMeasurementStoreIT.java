package dev.itobey.adapter.api.fddb.exporter.it;

import com.mongodb.client.MongoClients;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.BODY_FAT_PERCENT;
import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.WEIGHT_KG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Testcontainers
class BodyMeasurementStoreIT {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.9");

    @Test
    void retriesAndCorrectionsPreserveSourceScopedHistory() {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var store = new BodyMeasurementStore(new MongoTemplate(client, "measurements"));
            var day1 = Instant.parse("2026-01-01T12:00:00Z");
            var day2 = Instant.parse("2026-01-02T12:00:00Z");
            var original = new BodyMeasurement("samsung-health", "same-id", day1,
                    Map.of(WEIGHT_KG, 82.5, BODY_FAT_PERCENT, 24.0));
            var otherSource = new BodyMeasurement("garmin", "same-id", day1, Map.of(WEIGHT_KG, 82.6));
            var next = new BodyMeasurement("samsung-health", "next", day2, Map.of(WEIGHT_KG, 82.3));
            var corrected = new BodyMeasurement("samsung-health", "same-id", day1, Map.of(WEIGHT_KG, 82.4));

            store.importMeasurements(List.of(next, original, otherSource));
            store.importMeasurements(List.of(next, original, otherSource));
            assertThatIllegalArgumentException().isThrownBy(() -> store.importMeasurements(List.of(original, corrected)));
            assertThat(store.find(null, null, null, 0, 10)).containsExactly(otherSource, original, next);

            store.importMeasurements(List.of(corrected));
            assertThat(store.find(day1, day2, "samsung-health", 0, 10)).containsExactly(corrected);
            assertThat(store.find(day1.plusNanos(1), null, null, 0, 10)).containsExactly(next);
            assertThat(store.find(null, null, null, 1, 1)).containsExactly(corrected);
        }
    }
}
