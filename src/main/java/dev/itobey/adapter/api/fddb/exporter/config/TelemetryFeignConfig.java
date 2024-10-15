package dev.itobey.adapter.api.fddb.exporter.config;

import feign.auth.BasicAuthRequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TelemetryFeignConfig {

    private final FddbExporterProperties properties;

    @Bean
    public BasicAuthRequestInterceptor basicAuthRequestInterceptor() {
        return new BasicAuthRequestInterceptor(properties.getTelemetry().getUsername(),
                properties.getTelemetry().getToken());
    }
}
