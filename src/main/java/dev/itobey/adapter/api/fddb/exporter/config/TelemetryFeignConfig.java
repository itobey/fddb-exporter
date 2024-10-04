package dev.itobey.adapter.api.fddb.exporter.config;

import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryFeignConfig {

    @Value("${fddb-exporter.telemetry.username}")
    private String username;

    @Value("${fddb-exporter.telemetry.token}")
    private String token;

    @Bean
    public BasicAuthRequestInterceptor basicAuthRequestInterceptor() {
        return new BasicAuthRequestInterceptor(username, token);
    }
}
