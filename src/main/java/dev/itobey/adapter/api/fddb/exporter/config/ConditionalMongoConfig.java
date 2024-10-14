package dev.itobey.adapter.api.fddb.exporter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
@Import({MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
public class ConditionalMongoConfig {
    // This class is intentionally empty. Its purpose is to conditionally import MongoDB configurations.
}
