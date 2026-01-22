package dev.itobey.adapter.api.fddb.exporter.ui.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for retrieving health status information from health indicators.
 */
@Service
@RequiredArgsConstructor
public class HealthService {

    private final List<HealthIndicator> healthIndicators;
    private final FddbExporterProperties properties;

    /**
     * Get health status for all registered health indicators.
     *
     * @return Map of component names to their health status
     */
    public Map<String, ComponentHealth> getHealthStatus() {
        Map<String, ComponentHealth> components = new HashMap<>();

        // Get FDDB health indicator
        findHealthIndicator("fddb-login-check")
                .ifPresent(indicator -> {
                    Health health = indicator.health();
                    components.put("fddb-login-check", ComponentHealth.from(health));
                });

        // Get MongoDB health indicator (show as DISABLED if not enabled)
        if (isMongoDbEnabled()) {
            findHealthIndicator("mongodb")
                    .ifPresent(indicator -> {
                        Health health = indicator.health();
                        components.put("mongodb", ComponentHealth.from(health));
                    });
        } else {
            components.put("mongodb", ComponentHealth.disabled("MongoDB persistence is not enabled"));
        }

        // Get InfluxDB health indicator (show as DISABLED if not enabled)
        if (isInfluxDbEnabled()) {
            findHealthIndicator("influxdb")
                    .ifPresent(indicator -> {
                        Health health = indicator.health();
                        components.put("influxdb", ComponentHealth.from(health));
                    });
        } else {
            components.put("influxdb", ComponentHealth.disabled("InfluxDB persistence is not enabled"));
        }

        return components;
    }

    private Optional<HealthIndicator> findHealthIndicator(String name) {
        return healthIndicators.stream()
                .filter(indicator -> indicator.getClass().getAnnotation(org.springframework.stereotype.Component.class) != null)
                .filter(indicator -> {
                    String componentName = indicator.getClass().getAnnotation(org.springframework.stereotype.Component.class).value();
                    return name.equals(componentName);
                })
                .findFirst();
    }

    private boolean isMongoDbEnabled() {
        return properties.getPersistence() != null
                && properties.getPersistence().getMongodb() != null
                && properties.getPersistence().getMongodb().isEnabled();
    }

    private boolean isInfluxDbEnabled() {
        return properties.getPersistence() != null
                && properties.getPersistence().getInfluxdb() != null
                && properties.getPersistence().getInfluxdb().isEnabled();
    }

    /**
     * Simple wrapper for health component information.
     */
    @Getter
    public static class ComponentHealth {
        private final String status;
        private final Map<String, Object> details;

        private ComponentHealth(String status, Map<String, Object> details) {
            this.status = status;
            this.details = details;
        }

        public static ComponentHealth from(Health health) {
            return new ComponentHealth(
                    health.getStatus().getCode(),
                    health.getDetails()
            );
        }

        public static ComponentHealth disabled(String reason) {
            Map<String, Object> details = new HashMap<>();
            details.put("reason", reason);
            return new ComponentHealth("DISABLED", details);
        }
    }
}
