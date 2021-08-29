package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Handles the exports of data from FDDB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final FddbAdapter fddbAdapter;
    private final HtmlParser htmlParser;
    private final PersistenceService persistenceService;

    /**
     * Retrieve the data from FDDB, parse it and save it to the database.
     *
     * @param timeframe the timeframe to retrieve the data to
     */
    public void exportDataAndSaveToDb(Timeframe timeframe) throws AuthenticationException {
        FddbData dataToPersist = retrieveAndParseDataTo(timeframe);
        Optional<FddbData> optionalOfDbEntry = persistenceService.find(dataToPersist.getDate());
        // TODO unit test for handling of data not beeing present
        if (optionalOfDbEntry.isPresent() && dataToPersist.getKcal() != 0) {
            FddbData existingFddbData = optionalOfDbEntry.get();
            log.debug("updating existing database entry for {}", dataToPersist.getDate());
            FddbData updatedData = updateDataObject(dataToPersist, existingFddbData);
            persistenceService.save(updatedData);
            log.trace("updated entry");
        } else {
            log.debug("persisting new database entry");
            persistenceService.save(dataToPersist);
            log.trace("persisted entry");
        }
    }

    private FddbData retrieveAndParseDataTo(Timeframe timeframe) throws AuthenticationException {
        String response = fddbAdapter.retrieveDataToTimeframe(timeframe);
        FddbData fddbData = htmlParser.getDataFromResponse(response);
        LocalDateTime dateOfExport = LocalDateTime.ofEpochSecond(timeframe.getFrom(), 0, ZoneOffset.UTC);
        fddbData.setDate(Date.from(dateOfExport.toInstant(ZoneOffset.UTC)));
        log.info("handling dataset: {}", fddbData);
        return fddbData;
    }

    private FddbData updateDataObject(FddbData dataToPersist, FddbData existingFddbData) {
        // TODO maybe switch to Mapstruct
        existingFddbData.setCarbs(dataToPersist.getCarbs());
        existingFddbData.setFat(dataToPersist.getFat());
        existingFddbData.setFiber(dataToPersist.getFiber());
        existingFddbData.setKcal(dataToPersist.getKcal());
        existingFddbData.setSugar(dataToPersist.getSugar());
        existingFddbData.setProtein(dataToPersist.getProtein());
        return existingFddbData;
    }
}
