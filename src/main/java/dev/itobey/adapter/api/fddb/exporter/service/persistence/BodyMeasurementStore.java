package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class BodyMeasurementStore {
    public static final int MAX_IMPORT_RECORDS = 10_000;
    private static final String MEASURED_AT = "measurement.measuredAt";

    private final MongoTemplate mongoTemplate;

    @Document("bodyMeasurements")
    public record StoredMeasurement(@Id String id, BodyMeasurement measurement) {
    }

    public int importMeasurements(List<BodyMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty() || measurements.size() > MAX_IMPORT_RECORDS) {
            throw new IllegalArgumentException("Provide between 1 and " + MAX_IMPORT_RECORDS + " measurements");
        }
        Set<String> ids = new HashSet<>();
        for (BodyMeasurement measurement : measurements) {
            if (measurement == null || !ids.add(idFor(measurement))) {
                throw new IllegalArgumentException("The batch contains a null measurement or duplicate source and external ID");
            }
        }

        var bulk = mongoTemplate.bulkOps(BulkMode.UNORDERED, StoredMeasurement.class);
        for (BodyMeasurement measurement : measurements) {
            bulk.upsert(Query.query(Criteria.where("_id").is(idFor(measurement))),
                    new Update().set("measurement", measurement));
        }
        bulk.execute();
        return measurements.size();
    }

    public List<BodyMeasurement> find(Instant from, Instant to, String source, int page, int size) {
        if (from != null) {
            BodyMeasurement.validateTimestamp(from);
        }
        if (to != null) {
            BodyMeasurement.validateTimestamp(to);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("From must be before to");
        }
        if (page < 0 || size < 1 || size > 1000) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 1000");
        }

        Query query = new Query().with(Sort.by(MEASURED_AT, "_id")).skip((long) page * size).limit(size);
        if (from != null || to != null) {
            Criteria timestamp = Criteria.where(MEASURED_AT);
            // Mongo stores millisecond precision. Round both bounds up to keep the range half-open.
            if (from != null) {
                timestamp.gte(ceilMillis(from));
            }
            if (to != null) {
                timestamp.lt(ceilMillis(to));
            }
            query.addCriteria(timestamp);
        }
        if (source != null) {
            query.addCriteria(Criteria.where("measurement.source").is(source));
        }
        return mongoTemplate.find(query, StoredMeasurement.class).stream().map(StoredMeasurement::measurement).toList();
    }

    private static Instant ceilMillis(Instant value) {
        return Instant.ofEpochMilli(value.toEpochMilli() + (value.getNano() % 1_000_000 == 0 ? 0 : 1));
    }

    private static String idFor(BodyMeasurement measurement) {
        return measurement.source() + "/" + measurement.externalId();
    }
}
