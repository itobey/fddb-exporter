package dev.itobey.adapter.api.fddb.exporter;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        MongoAutoConfiguration.class
})
@EnableScheduling
@EnableFeignClients
@EnableConfigurationProperties(FddbExporterProperties.class)
@EnableEncryptableProperties
public class ExporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExporterApplication.class, args);
    }

}
