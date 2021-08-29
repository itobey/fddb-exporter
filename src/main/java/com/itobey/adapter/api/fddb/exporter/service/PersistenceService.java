package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.repository.FddbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

/**
 * Handels the persistence layer of the application.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private final FddbRepository fddbRepository;

    /**
     * Retrieves an entry to a given date.
     *
     * @param date the date to find an entry to
     * @return an Optional of {@link FddbData}
     */
    public Optional<FddbData> find(Date date) {
        return fddbRepository.findFirstByDate(date);
    }

    /**
     * Persists the {@link FddbData} object to the database.
     *
     * @param fddbData the object containing the data
     * @return the updated object saved to the database
     */
    public FddbData save(FddbData fddbData) {
        return fddbRepository.save(fddbData);
    }

}
