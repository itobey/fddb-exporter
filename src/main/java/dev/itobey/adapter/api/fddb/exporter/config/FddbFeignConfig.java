package dev.itobey.adapter.api.fddb.exporter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FddbFeignConfig {

    @Autowired
    private FddbExporterProperties properties;

    @Bean
    public FddbRequestInterceptor fddbRequestInterceptor() {
        return new FddbRequestInterceptor(properties);
    }
}