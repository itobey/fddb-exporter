package dev.itobey.adapter.api.fddb.exporter.config;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

// Indexes are created explicitly here, on ApplicationReadyEvent, instead of declaratively via
// spring.data.mongodb.auto-index-creation. That setting builds indexes synchronously while the
// mongoTemplate bean itself is created, so any failure (Mongo unreachable, duplicate dates already
// in the collection) crashes application startup entirely. Building them after the context is up,
// with the failure caught and logged, means a startup hiccup or a pre-existing duplicate degrades to
// "unique date is not enforced yet" instead of the whole application refusing to start.
@Configuration
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(FddbData.class);
            indexOps.createIndex(new Index().on("date", Sort.Direction.ASC).unique());
            indexOps.createIndex(new Index().on("products.name", Sort.Direction.ASC));
        } catch (Exception e) {
            log.error("Failed to create MongoDB indexes. If this is the unique index on 'date', the collection "
                    + "likely already contains duplicate dates - see the upgrading docs to find and remove them, "
                    + "then restart. Continuing startup without the index in the meantime.", e);
        }
    }

}
