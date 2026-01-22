package dev.itobey.adapter.api.fddb.exporter.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for MongoDB connectivity.
 * Only active when MongoDB persistence is enabled.
 * Checks actual connectivity to MongoDB and reports status.
 */
@Component("mongodb")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class MongoDbHealthIndicator implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    @Override
    public Health health() {
        try {
            log.debug("Checking MongoDB health via ping command");
            // Execute a simple command to check connectivity
            var result = mongoTemplate.executeCommand("{ ping: 1 }");

            if (result.get("ok") != null && result.get("ok").equals(1.0)) {
                return Health.up()
                        .withDetail("database", mongoTemplate.getDb().getName())
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "MongoDB ping command failed")
                        .build();
            }
        } catch (Exception exception) {
            log.error("MongoDB health check failed", exception);
            return Health.down()
                    .withDetail("error", exception.getMessage())
                    .build();
        }
    }
}
