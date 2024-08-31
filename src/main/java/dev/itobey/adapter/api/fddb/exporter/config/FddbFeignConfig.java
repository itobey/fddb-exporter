package dev.itobey.adapter.api.fddb.exporter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FddbFeignConfig {

    @Bean
    public FddbRequestInterceptor fddbRequestInterceptor(
            @Value("${fddb-exporter.fddb.username}") String username,
            @Value("${fddb-exporter.fddb.password}") String password) {
        return new FddbRequestInterceptor(username, password);
    }
}