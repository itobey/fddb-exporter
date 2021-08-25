package com.itobey.adapter.api.fddb.exporter.service;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.repository.FddbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * The service to handle manual export requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualExporterService {

    private final TimeframeCalculator timeframeCalculator;
    private final FddbAdapter fddbAdapter;
    private final HtmlParser htmlParser;
    private final FddbRepository fddbRepository;

    /**
     * Export data for all days contained in the given timeframe as a batch.
     *
     * @param fddbBatchExport the data which should be exported
     */
    public void exportBatch(FddbBatchExport fddbBatchExport) throws ManualExporterException, AuthenticationException {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fddbBatchExport.getFromDate());
            to = LocalDate.parse(fddbBatchExport.getToDate());
        } catch (DateTimeParseException dateTimeParseException) {
            log.error("payload cannot be parsed");
            throw dateTimeParseException;
        }

        if (from.isAfter(to)) {
            throw new ManualExporterException("the 'from' date cannot be after the 'to' date");
        }

        long amountDaysToExport = DAYS.between(from, to) + 1;

        // export days between the given dates
        for (int i = 0; i < amountDaysToExport; i++) {
            Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(from);
            exportDataAndSaveToDb(timeframe);
            from = from.plusDays(1);
        }

    }

    /**
     * Retrieve the data from FDDB, parse it and save it to the database.
     *
     * @param timeframe the timeframe to retrieve the data to
     */
    protected void exportDataAndSaveToDb(Timeframe timeframe) throws AuthenticationException {
        String response = fddbAdapter.retrieveDataToTimeframe(timeframe);
        FddbData fddbData = htmlParser.getDataFromResponse(response);
        LocalDateTime dateOfExport = LocalDateTime.ofEpochSecond(timeframe.getFrom(), 0, ZoneOffset.UTC);
        fddbData.setDate(Date.from(dateOfExport.toInstant(ZoneOffset.UTC)));
        log.info(fddbData.toString());
        fddbRepository.save(fddbData);
    }

}
