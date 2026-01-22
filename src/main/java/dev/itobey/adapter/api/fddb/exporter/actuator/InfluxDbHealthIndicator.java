package dev.itobey.adapter.api.fddb.exporter.actuator;

import com.influxdb.client.InfluxDBClient;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for InfluxDB connectivity.
 * Only active when InfluxDB persistence is enabled.
 * Checks actual connectivity to InfluxDB and reports status.
 */
@Component("influxdb")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "fddb-exporter.persistence.influxdb.enabled", havingValue = "true")
public class InfluxDbHealthIndicator implements HealthIndicator {

    private final InfluxDBClient influxDBClient;
    private final FddbExporterProperties properties;

    @Override
    public Health health() {
        try {
            log.debug("Checking InfluxDB health");
            boolean isAlive = influxDBClient.ping();

            if (isAlive) {
                return Health.up()
                        .withDetail("bucket", properties.getInfluxdb().getBucket())
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "InfluxDB ping failed")
                        .build();
            }
        } catch (Exception e) {
            log.error("InfluxDB health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
