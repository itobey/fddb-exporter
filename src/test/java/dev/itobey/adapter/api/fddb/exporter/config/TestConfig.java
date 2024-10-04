package dev.itobey.adapter.api.fddb.exporter.config;

import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * This class is used to mock the service beans for testing purposes.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public TelemetryService telemetryService() {
        return Mockito.mock(TelemetryService.class);
    }

}
