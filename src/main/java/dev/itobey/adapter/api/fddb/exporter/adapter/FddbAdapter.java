package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adapter to the FDDB API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FddbAdapter {

    private final FddbApi fddbApi;

    public String retrieveDataToTimeframe(TimeframeDTO timeframeDTO) {
        log.debug("retrieving fddb data for timeframe {}", timeframeDTO);
        return fddbApi.getDiary(timeframeDTO.getFrom(), timeframeDTO.getTo());
    }

}
