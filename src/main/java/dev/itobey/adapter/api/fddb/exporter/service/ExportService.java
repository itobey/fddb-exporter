package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final FddbAdapter fddbAdapter;
    private final FddbParserService fddbParserService;

    public FddbData exportData(TimeframeDTO timeframeDTO) throws AuthenticationException, ParseException {
        String response = fddbAdapter.retrieveDataToTimeframe(timeframeDTO);
        log.trace("HTML response: {}", response);
        FddbData fddbData = fddbParserService.parseDiary(response);
        LocalDateTime dateOfExport = LocalDateTime.ofEpochSecond(timeframeDTO.getFrom(), 0, ZoneOffset.UTC);
        fddbData.setDate(dateOfExport.toLocalDate());
        log.debug("handling dataset: {}", fddbData);
        return fddbData;
    }

}
