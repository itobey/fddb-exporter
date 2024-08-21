package dev.itobey.adapter.api.fddb.exporter;

import dev.itobey.adapter.api.fddb.exporter.config.ConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(ConfigProperties.class)
@EnableScheduling
@EnableFeignClients
public class ExporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExporterApplication.class, args);
    }

}
