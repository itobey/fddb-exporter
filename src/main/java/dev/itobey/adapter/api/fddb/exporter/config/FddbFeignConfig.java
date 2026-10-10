package dev.itobey.adapter.api.fddb.exporter.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
public class FddbFeignConfig {

    private final FddbExporterProperties properties;
    private final FddbSession fddbSession;

    @Bean
    public FddbRequestInterceptor fddbRequestInterceptor() {
        return new FddbRequestInterceptor(properties, fddbSession);
    }
}