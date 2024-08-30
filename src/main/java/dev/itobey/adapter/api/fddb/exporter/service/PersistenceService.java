package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Retrieves all {@link FddbData} objects stored in the database.
     *
     * @return a list of all {@link FddbData} objects
     */
    public List<FddbData> findAllEntries() {
        return fddbDataRepository.findAll();
    }

    /**
     * Retrieves an entry to a given date.
     *
     * @param date the date to find an entry to
     * @return an Optional of {@link FddbData}
     */
    public Optional<FddbData> findByDate(LocalDate date) {
        return fddbDataRepository.findFirstByDate(date);
    }

    /**
     * Searches for {@link FddbData} objects by product name using a fuzzy search.
     *
     * @param name the product name to search for
     * @return a list of {@link FddbData} objects matching the search criteria
     */
    public List<FddbData> findByProduct(String name) {
        return fddbDataRepository.findByProductNameFuzzy(name);
    }

    public void saveOrUpdate(FddbData dataToPersist) {
        Optional<FddbData> optionalOfDbEntry = findByDate(dataToPersist.getDate());
        if (optionalOfDbEntry.isPresent()) {
            FddbData existingFddbData = optionalOfDbEntry.get();
            log.debug("updating existing database entry for {}", dataToPersist.getDate());
            fddbDataMapper.updateFddbData(dataToPersist, existingFddbData);
            FddbData updatedEntry = fddbDataRepository.save(existingFddbData);
            log.info("updated entry: {}", updatedEntry);
        } else {
            FddbData savedEntry = fddbDataRepository.save(dataToPersist);
            log.info("created entry: {}", savedEntry);
        }
    }

}
