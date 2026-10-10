package dev.itobey.adapter.api.fddb.exporter.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;

/**
 * Refuses to start when neither persistence layer is enabled - there would be nowhere to put the data.
 */
@Configuration
@Slf4j
@ConditionalOnExpression("${fddb-exporter.persistence.mongodb.enabled} == false && ${fddb-exporter.persistence.influxdb.enabled} == false")
public class PersistenceCheckConfig {

    /**
     * Throws rather than calling {@code System.exit(1)}: a failing {@code @PostConstruct} fails the context
     * with a clear message and a non-zero exit code, which is exactly the wanted behaviour. {@code System.exit}
     * during context refresh triggers Spring's own shutdown hook, which then waits for the refresh that is
     * calling it - a known hang - and it makes this class untestable.
     */
    @PostConstruct
    public void failFast() {
        throw new IllegalStateException("Both MongoDB and InfluxDB are disabled. At least one persistence layer "
                + "is required - set fddb-exporter.persistence.mongodb.enabled or "
                + "fddb-exporter.persistence.influxdb.enabled to true.");
    }
}
