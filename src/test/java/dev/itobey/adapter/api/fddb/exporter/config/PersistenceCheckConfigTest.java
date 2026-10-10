package dev.itobey.adapter.api.fddb.exporter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceCheckConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceCheckConfig.class);

    @Test
    void context_shouldFailToStart_whenBothPersistenceLayersAreDisabled() {
        contextRunner
                .withPropertyValues(
                        "fddb-exporter.persistence.mongodb.enabled=false",
                        "fddb-exporter.persistence.influxdb.enabled=false")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("At least one persistence layer is required"));
    }

    @Test
    void context_shouldStart_whenMongoDbIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "fddb-exporter.persistence.mongodb.enabled=true",
                        "fddb-exporter.persistence.influxdb.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
