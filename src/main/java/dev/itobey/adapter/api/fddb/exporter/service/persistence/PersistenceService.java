package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.config.CacheConfig;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
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
public class PersistenceService {

    private final FddbDataMapper fddbDataMapper;
    private final FddbExporterProperties properties;

    /**
     * Optional store dependencies are null when unavailable; store-enabled checks guard write access.
     */
    private final FddbDataRepository fddbDataRepository;
    private final MongoDBService mongoDBService;
    private final InfluxDBService influxDBService;

    public PersistenceService(FddbDataMapper fddbDataMapper,
                              FddbExporterProperties properties,
                              ObjectProvider<FddbDataRepository> fddbDataRepository,
                              ObjectProvider<MongoDBService> mongoDBService,
                              ObjectProvider<InfluxDBService> influxDBService) {
        this.fddbDataMapper = fddbDataMapper;
        this.properties = properties;
        this.fddbDataRepository = fddbDataRepository.getIfAvailable();
        this.mongoDBService = mongoDBService.getIfAvailable();
        this.influxDBService = influxDBService.getIfAvailable();
    }

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

    public List<ProductWithDate> findByProduct(String name, List<DayOfWeek> daysOfWeek) {
        return mongoDBService.findByProduct(name, daysOfWeek);
    }

    public List<ProductWithDate> findByProduct(String name, List<DayOfWeek> daysOfWeek,
                                               LocalDate fromDate, LocalDate toDate, Integer limit) {
        return mongoDBService.findByProduct(name, daysOfWeek, fromDate, toDate, limit);
    }

    public ProductSummaryDTO getProductSummary(String name, LocalDate fromDate, LocalDate toDate) {
        return mongoDBService.getProductSummary(name, fromDate, toDate);
    }

    public List<TopProductDTO> getTopProducts(ProductRanking ranking, LocalDate fromDate, LocalDate toDate, int limit) {
        return mongoDBService.getTopProducts(ranking, fromDate, toDate, limit);
    }

    public List<String> findDistinctProductNames(String search, int limit) {
        return mongoDBService.findDistinctProductNames(search, limit);
    }

    public List<ProductWithDate> findByProductsWithExclusions(List<String> includeNames, List<String> excludeNames,
                                                              LocalDate startDate) {
        return mongoDBService.findByProductsWithExclusions(includeNames, excludeNames, startDate);
    }

    public List<DayWithProductsDTO> findDaysWithProducts(List<String> includeNames, List<String> excludeNames,
                                                         LocalDate startDate, int limit) {
        return mongoDBService.findDaysWithProducts(includeNames, excludeNames, startDate, limit);
    }

    public ProductDayTotalsDTO countDaysWithProducts(List<String> includeNames, List<String> excludeNames,
                                                     LocalDate startDate) {
        return mongoDBService.countDaysWithProducts(includeNames, excludeNames, startDate);
    }

    public Optional<FddbData> findByDate(LocalDate date) {
        return mongoDBService.findByDate(date);
    }

    public List<FddbData> findByDateBetween(LocalDate fromDate, LocalDate toDate) {
        return mongoDBService.findByDateBetween(fromDate, toDate);
    }

    /**
     * Saves diary data and clears the statistics cache after a successful write.
     */
    @CacheEvict(value = CacheConfig.STATS_CACHE, allEntries = true)
    public void saveOrUpdate(FddbData dataToPersist) {
        saveToMongoDbIfEnabled(dataToPersist);
        saveToInfluxDbIfEnabled(dataToPersist);
    }

    private void saveToInfluxDbIfEnabled(FddbData dataToPersist) {
        if (properties.getPersistence().getInfluxdb().isEnabled()) {
            log.info("writing point to influxdb: {}", dataToPersist.toDailyTotalsString());
            influxDBService.saveToInfluxDB(dataToPersist);
        }
    }

    private void saveToMongoDbIfEnabled(FddbData dataToPersist) {
        if (properties.getPersistence().getMongodb().isEnabled()) {
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
