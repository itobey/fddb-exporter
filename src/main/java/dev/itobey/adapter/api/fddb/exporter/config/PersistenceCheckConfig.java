package dev.itobey.adapter.api.fddb.exporter.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ConditionalOnExpression("${fddb-exporter.persistence.mongodb.enabled} == false && ${fddb-exporter.persistence.influxdb.enabled} == false")
public class PersistenceCheckConfig {

    @PostConstruct
    public void exitApplication() {
        log.error("ERROR: Both MongoDB and InfluxDB are disabled. At least one persistence layer must be enabled.");
        System.exit(1);
    }

    @Bean
    public String dummyBean() {
        return "Dummy bean to ensure configuration class is loaded";
    }
}
