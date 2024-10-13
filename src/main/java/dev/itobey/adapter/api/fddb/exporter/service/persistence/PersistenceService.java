package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Provides persistence-related services for managing {@link FddbData} objects.
 * <p>
 * This service class is responsible for saving and retrieving {@link FddbData} objects to/from the database.
 * It provides methods to find the first {@link FddbData} object by a given date, save a new {@link FddbData} object,
 * and search for {@link FddbData} objects by product name.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private final FddbDataRepository fddbDataRepository;
    private final FddbDataMapper fddbDataMapper;
    private final InfluxDBService influxDBService;
    private final MongoDBService mongoDBService;

    @Value("${fddb-exporter.persistence.mongodb.enabled}")
    private boolean mongodbEnabled;

    @Value("${fddb-exporter.persistence.influxdb.enabled}")
    private boolean influxdbEnabled;

    public long countAllEntries() {
        return mongoDBService.countAllEntries();
    }

    public long countAllInfluxDbPoints() {
        return influxDBService.getDataPointCount();
    }

    public List<FddbData> findAllEntries() {
        return mongoDBService.findAllEntries();
    }

    public List<ProductWithDate> findByProduct(String name) {
        return mongoDBService.findByProduct(name);
    }

    public Optional<FddbData> findByDate(LocalDate date) {
        return mongoDBService.findByDate(date);
    }

    public void saveOrUpdate(FddbData dataToPersist) {
        saveToMongoDbIfEnabled(dataToPersist);
        saveToInfluxDbIfEnabled(dataToPersist);
    }

    private void saveToInfluxDbIfEnabled(FddbData dataToPersist) {
        if (influxdbEnabled) {
            log.info("writing point to influxdb: {}", dataToPersist);
            influxDBService.saveToInfluxDB(dataToPersist);
        }
    }

    private void saveToMongoDbIfEnabled(FddbData dataToPersist) {
        if (mongodbEnabled) {
            Optional<FddbData> optionalOfDbEntry = mongoDBService.findByDate(dataToPersist.getDate());
            if (optionalOfDbEntry.isPresent()) {
                FddbData existingFddbData = optionalOfDbEntry.get();
                log.debug("updating existing database entry for {}", dataToPersist.getDate());
                updateDataIfNotIdentical(dataToPersist, existingFddbData);
            } else {
                FddbData savedEntry = fddbDataRepository.save(dataToPersist);
                log.info("created entry in database: {}", savedEntry);
            }
        }
    }

    private void updateDataIfNotIdentical(FddbData dataToPersist, FddbData existingFddbData) {
        if (!dataToPersist.equals(existingFddbData)) {
            fddbDataMapper.updateFddbData(existingFddbData, dataToPersist);
            FddbData updatedEntry = fddbDataRepository.save(existingFddbData);
            log.info("updated entry: {}", updatedEntry);
        } else {
            log.info("entry already exported, skipping: {}", dataToPersist);
        }
    }

}
