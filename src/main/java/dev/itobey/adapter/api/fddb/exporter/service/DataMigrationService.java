package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.InfluxDBService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = {
        "fddb-exporter.persistence.mongodb.enabled",
        "fddb-exporter.persistence.influxdb.enabled"
}, havingValue = "true")
public class DataMigrationService {

    private final MongoDBService mongoDbService;
    private final InfluxDBService influxDbService;

    public int migrateMongoDbEntriesToInfluxDb() {
        List<FddbData> allEntries = mongoDbService.findAllEntries();
        int amountEntries = allEntries.size();
        log.info("migrating {} entries from MongoDB to InfluxDB...", amountEntries);
        allEntries.forEach(influxDbService::saveToInfluxDB);
        log.info("migration completed");
        return amountEntries;
    }

}
