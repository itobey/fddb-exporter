package dev.itobey.adapter.api.fddb.exporter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FddbFeignConfig {

    @Bean
    public FddbRequestInterceptor fddbRequestInterceptor() {
        return new FddbRequestInterceptor();
    }
}